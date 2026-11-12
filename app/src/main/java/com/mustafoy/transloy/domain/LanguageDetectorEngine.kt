package com.mustafoy.transloy.domain

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await

class LanguageDetectorEngine {

    private val identifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.35f)
                .build()
        )
    }

    /**
     * Offline ML Kit Language Identification (< 5ms) + Unicode Script Fallback
     * so even 2-character game buttons (like "開始" or "시작") auto-detect reliably.
     */
    suspend fun detectLanguageCode(sampleText: String): String {
        if (sampleText.isBlank()) return "en"

        val mlDetected = runCatching {
            identifier.identifyLanguage(sampleText.take(500)).await()
        }.getOrDefault("und")

        if (mlDetected != "und" && mlDetected.isNotBlank()) {
            return normalizeBcp47(mlDetected)
        }

        // Fallback via Unicode character range inspection
        return detectByUnicodeScript(sampleText)
    }

    private fun normalizeBcp47(tag: String): String {
        val base = tag.substringBefore("-").lowercase()
        return when (base) {
            "iw" -> "he"
            "in" -> "id"
            else -> base
        }
    }

    private fun detectByUnicodeScript(text: String): String {
        for (c in text) {
            when {
                c in '\u3040'..'\u30FF' -> return "ja"
                c in '\uAC00'..'\uD7AF' -> return "ko"
                c in '\u4E00'..'\u9FFF' -> return "zh"
                c in '\u0600'..'\u06FF' -> return "ar"
                c in '\u0900'..'\u097F' -> return "hi"
                c in '\u0400'..'\u04FF' -> return "ru"
                c in '\u0E00'..'\u0E7F' -> return "th"
            }
        }
        return "en"
    }
}
