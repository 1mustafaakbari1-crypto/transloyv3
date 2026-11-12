package com.mustafoy.transloy.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mustafoy.transloy.model.EngineModeUsed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HybridTranslationEngine(private val context: Context) {

    companion object {
        /**
         * OPTIONAL FOR MASS PRODUCTION SCALE:
         * Paste your official Google Cloud Translation v2 API Key here if publishing to millions of users.
         * When blank, it uses the fast zero-config GTX online endpoint + seamless ML Kit Offline fallback.
         */
        private const val GOOGLE_CLOUD_API_KEY = ""
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelManager = RemoteModelManager.getInstance()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Hybrid Translation Flow:
     * 1. If Online -> Instant Cloud Translation + Auto-Downloads Offline Pack in background.
     * 2. If Cloud times out OR user is Offline -> Uses On-Device ML Kit Offline AI.
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLangCode: String,
        targetLangCode: String,
        autoDownloadOffline: Boolean
    ): Pair<List<String>, EngineModeUsed> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList<String>() to EngineModeUsed.OFFLINE

        if (sourceLangCode.equals(targetLangCode, ignoreCase = true)) {
            return@withContext texts to EngineModeUsed.OFFLINE
        }

        val onlineAvailable = isOnline()

        if (onlineAvailable && autoDownloadOffline) {
            // Silently prepare offline packs in background so future offline usage works automatically
            backgroundScope.launch {
                runCatching { downloadOfflineModel(sourceLangCode) }
                runCatching { downloadOfflineModel(targetLangCode) }
            }
        }

        // Try fast Online Cloud first when connected
        if (onlineAvailable) {
            val onlineResults = runCatching {
                texts.map { text ->
                    async { translateSingleOnline(text, sourceLangCode, targetLangCode) }
                }.awaitAll()
            }.getOrNull()

            if (onlineResults != null && onlineResults.all { it.isNotBlank() }) {
                return@withContext onlineResults to EngineModeUsed.ONLINE
            }
        }

        // Fallback or Primary Offline Mode: Google ML Kit On-Device Translation
        val offlineResults = translateBatchOffline(texts, sourceLangCode, targetLangCode)
        return@withContext offlineResults to EngineModeUsed.OFFLINE
    }

    private fun translateSingleOnline(text: String, source: String, target: String): String {
        if (GOOGLE_CLOUD_API_KEY.isNotBlank()) {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translation.googleapis.com/language/translate/v2" +
                    "?key=$GOOGLE_CLOUD_API_KEY&q=$encoded&target=$target&format=text"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Cloud HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                return JSONObject(body)
                    .getJSONObject("data")
                    .getJSONArray("translations")
                    .getJSONObject(0)
                    .getString("translatedText")
            }
        } else {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val sl = source.ifBlank { "auto" }
            val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=$sl&tl=$target&dt=t&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("GTX HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val outer = JSONArray(body).getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until outer.length()) {
                    val seg = outer.getJSONArray(i).optString(0)
                    sb.append(seg)
                }
                return sb.toString().trim()
            }
        }
    }

    private suspend fun translateBatchOffline(
        texts: List<String>,
        sourceCode: String,
        targetCode: String
    ): List<String> {
        val mlSource = TranslateLanguage.fromLanguageTag(sourceCode) ?: TranslateLanguage.ENGLISH
        val mlTarget = TranslateLanguage.fromLanguageTag(targetCode) ?: TranslateLanguage.ENGLISH

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(mlSource)
            .setTargetLanguage(mlTarget)
            .build()

        val translator = Translation.getClient(options)
        return try {
            val sourceReady = isOfflineModelDownloaded(mlSource)
            val targetReady = isOfflineModelDownloaded(mlTarget)

            if (!sourceReady || !targetReady) {
                if (isOnline()) {
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                } else {
                    return texts.map { "⚠️ Offline pack (${sourceCode.uppercase()} / ${targetCode.uppercase()}) not downloaded yet" }
                }
            }

            texts.map { text ->
                runCatching { translator.translate(text).await() }.getOrDefault(text)
            }
        } finally {
            translator.close()
        }
    }

    // --- Offline Model Management APIs for the Main App UI ---

    suspend fun getDownloadedModelCodes(): Set<String> {
        val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
        return models.map { it.language }.toSet()
    }

    suspend fun isOfflineModelDownloaded(langCode: String): Boolean {
        val mlCode = TranslateLanguage.fromLanguageTag(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlCode).build()
        return modelManager.isModelDownloaded(model).await()
    }

    suspend fun downloadOfflineModel(langCode: String): Boolean {
        val mlCode = TranslateLanguage.fromLanguageTag(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlCode).build()
        val conditions = DownloadConditions.Builder().build()
        return runCatching {
            modelManager.download(model, conditions).await()
            true
        }.getOrDefault(false)
    }

    suspend fun deleteOfflineModel(langCode: String): Boolean {
        val mlCode = TranslateLanguage.fromLanguageTag(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlCode).build()
        return runCatching {
            modelManager.deleteDownloadedModel(model).await()
            true
        }.getOrDefault(false)
    }
}
