package com.mustafoy.transloy.model

import android.graphics.Rect

enum class OcrScriptMode(val label: String) {
    AUTO_ALL("✨ Auto (All Languages & Manga/Games)"),
    LATIN("Latin (English, Spanish, French, Turkish...)"),
    JAPANESE("Japanese (Kanji, Hiragana, Katakana)"),
    CHINESE("Chinese (Simplified & Traditional)"),
    KOREAN("Korean (Hangul)"),
    DEVANAGARI("Devanagari (Hindi, Marathi, Nepali)")
}

enum class EngineModeUsed(val badge: String) {
    ONLINE("⚡ Online Cloud"),
    OFFLINE("📴 Offline AI")
}

data class OcrTextBlock(
    val originalText: String,
    val boundingBox: Rect
)

data class TranslatedBlock(
    val originalText: String,
    val translatedText: String,
    val boundingBox: Rect,
    val backgroundColor: Int,
    val textColor: Int
)

data class ScreenTranslationResult(
    val blocks: List<TranslatedBlock>,
    val detectedSourceLang: String,
    val targetLang: String,
    val engineUsed: EngineModeUsed
)

data class LanguageOption(
    val code: String,
    val name: String,
    val nativeName: String
)

object SupportedLanguages {
    val list = listOf(
        LanguageOption("en", "English", "English"),
        LanguageOption("ar", "Arabic", "العربية"),
        LanguageOption("ur", "Urdu", "اردو"),
        LanguageOption("es", "Spanish", "Español"),
        LanguageOption("fr", "French", "Français"),
        LanguageOption("de", "German", "Deutsch"),
        LanguageOption("tr", "Turkish", "Türkçe"),
        LanguageOption("ru", "Russian", "Русский"),
        LanguageOption("hi", "Hindi", "हिन्दी"),
        LanguageOption("zh", "Chinese", "中文"),
        LanguageOption("ja", "Japanese", "日本語"),
        LanguageOption("ko", "Korean", "한국어"),
        LanguageOption("pt", "Portuguese", "Português"),
        LanguageOption("it", "Italian", "Italiano"),
        LanguageOption("id", "Indonesian", "Bahasa Indonesia"),
        LanguageOption("vi", "Vietnamese", "Tiếng Việt"),
        LanguageOption("th", "Thai", "ไทย"),
        LanguageOption("fa", "Persian", "فارسی"),
        LanguageOption("bn", "Bengali", "বাংলা"),
        LanguageOption("uz", "Uzbek", "Oʻzbek")
    )

    fun nameFor(code: String): String {
        if (code == "und" || code == "auto") return "Auto"
        return list.find { it.code.equals(code, ignoreCase = true) }?.name ?: code.uppercase()
    }
}
