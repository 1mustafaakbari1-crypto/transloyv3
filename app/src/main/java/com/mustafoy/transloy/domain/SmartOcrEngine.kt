package com.mustafoy.transloy.domain

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mustafoy.transloy.model.OcrScriptMode
import com.mustafoy.transloy.model.OcrTextBlock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class SmartOcrEngine {

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val japaneseRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val koreanRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
    private val devanagariRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun extractTextBlocks(bitmap: Bitmap, mode: OcrScriptMode): List<OcrTextBlock> {
        val image = InputImage.fromBitmap(bitmap, 0)

        val visionText: Text? = when (mode) {
            OcrScriptMode.LATIN -> latinRecognizer.process(image).await()
            OcrScriptMode.JAPANESE -> japaneseRecognizer.process(image).await()
            OcrScriptMode.CHINESE -> chineseRecognizer.process(image).await()
            OcrScriptMode.KOREAN -> koreanRecognizer.process(image).await()
            OcrScriptMode.DEVANAGARI -> devanagariRecognizer.process(image).await()
            OcrScriptMode.AUTO_ALL -> recognizeSmartMultiScript(image)
        }

        return visionText?.textBlocks?.mapNotNull { block ->
            val rect = block.boundingBox ?: return@mapNotNull null
            val cleaned = block.text.replace("\n", " ").trim()
            // Ignore single stray symbols/numbers that don't need translation
            if (cleaned.length < 2 || cleaned.all { it.isDigit() || !it.isLetter() }) {
                null
            } else {
                OcrTextBlock(originalText = cleaned, boundingBox = rect)
            }
        } ?: emptyList()
    }

    /**
     * Runs multi-script recognizers concurrently and picks the recognizer that
     * captures the highest meaningful script score (handles Latin, Manga JP, CN, KR, Hindi).
     */
    private suspend fun recognizeSmartMultiScript(image: InputImage): Text? = coroutineScope {
        val jpDeferred = async { runCatching { japaneseRecognizer.process(image).await() }.getOrNull() }
        val cnDeferred = async { runCatching { chineseRecognizer.process(image).await() }.getOrNull() }
        val krDeferred = async { runCatching { koreanRecognizer.process(image).await() }.getOrNull() }
        val hiDeferred = async { runCatching { devanagariRecognizer.process(image).await() }.getOrNull() }
        val latDeferred = async { runCatching { latinRecognizer.process(image).await() }.getOrNull() }

        val candidates = listOfNotNull(
            jpDeferred.await()?.let { it to scoreScriptRichness(it, ::isJapaneseKana) },
            krDeferred.await()?.let { it to scoreScriptRichness(it, ::isKoreanHangul) },
            hiDeferred.await()?.let { it to scoreScriptRichness(it, ::isDevanagari) },
            cnDeferred.await()?.let { it to scoreScriptRichness(it, ::isCjkIdeograph) },
            latDeferred.await()?.let { it to it.text.count { ch -> ch.isLetter() } }
        )

        candidates.maxByOrNull { it.second }?.first
    }

    private fun scoreScriptRichness(text: Text, nativeMatcher: (Char) -> Boolean): Int {
        val raw = text.text
        val nativeCount = raw.count(nativeMatcher)
        val totalLetters = raw.count { it.isLetter() }
        // Give bonus weight if recognizer found genuine script-specific characters
        return if (nativeCount > 0) totalLetters + (nativeCount * 3) else totalLetters
    }

    private fun isJapaneseKana(c: Char): Boolean =
        c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF'

    private fun isKoreanHangul(c: Char): Boolean =
        c in '\uAC00'..'\uD7AF' || c in '\u1100'..'\u11FF'

    private fun isDevanagari(c: Char): Boolean =
        c in '\u0900'..'\u097F'

    private fun isCjkIdeograph(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF'
}
