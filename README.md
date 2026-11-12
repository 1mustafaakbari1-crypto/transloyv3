# TRANSLOY v2.0 — Screen Translator for Android

> **Zero-Disturbance Human-Centered UI & UX • Google Lens Style Chameleon AR Overlay • Smart Multi-Script OCR • Hybrid Online + Offline Translation**

TRANSLOY is a modern, production-grade Android screen translation application built with **Jetpack Compose (Material 3)**, **Android Accessibility Service API**, **Google ML Kit Vision & Translation V2**, and an instantaneous **Online/Offline Hybrid Engine**.

---

## 🌟 Why TRANSLOY Solves Common Screen Translator Pitfalls

Most custom Android screen-translator prototypes fail for four specific technical reasons:

1. **The `MediaProjection` Crash Problem:**
   * Traditional apps use `MediaProjection` to capture the screen, which on Android 12–15 triggers recurring system popups ("Start recording or casting?") and crashes foreground services.
   * **TRANSLOY's Solution:** Uses Android's **`AccessibilityService.takeScreenshot()` + `TYPE_ACCESSIBILITY_OVERLAY`** (Android 11+ / API 30+). The user grants **one permission once**. Zero popups, zero foreground service crashes, and no separate "Draw Over Other Apps" permission needed.

2. **Hardware Bitmap Crashes:**
   * Screenshots returned by Android 11+ are `Config.HARDWARE` bitmaps, which crash if pixel colors are sampled directly.
   * **TRANSLOY's Solution:** Safely converts `HardwareBuffer` to in-memory `ARGB_8888` software bitmaps and immediately closes native buffers to prevent memory leaks.

3. **Latin-Only OCR Failure:**
   * Standard ML Kit OCR only reads Latin characters and fails completely on Japanese manga, Chinese games, Korean webtoons, or Hindi text.
   * **TRANSLOY's Solution:** `SmartOcrEngine` bundles all **5 ML Kit V2 Recognizers** (Latin, Japanese, Chinese, Korean, Devanagari) with automatic script-scoring heuristics.

4. **Slow or Broken Offline/Online Switching:**
   * Waiting for a 30MB offline language model on first click freezes traditional apps.
   * **TRANSLOY's Solution:** **Smart Hybrid Engine**:
     * **If Online:** Translates instantaneously via Cloud API (<300ms) and silently pre-downloads the detected language's offline pack in the background.
     * **If Offline (or slow internet > 2.5s):** Automatically falls back to **On-Device ML Kit Offline AI** with 0ms network delay.

---

## 🎨 UI & UX Architecture

### 1. The "Ghost Edge" Floating Bubble
* **Active State:** A sleek `52dp` soft indigo circle with a crisp translate glyph and status ring.
* **Auto-Ghost Idle State:** After **3 seconds** of inactivity, the bubble magnetically docks to the left or right screen edge, tucks 38% of its body into the bezel, and fades to **38% opacity**, never blocking keyboards, videos, or game controls.
* **Interactions:**
  * **1 Tap:** Hides itself -> Captures screen -> Shows a smooth breathing pulse -> Displays translated screen in ~0.6s.
  * **Drag:** Smooth touch tracking with decelerating edge snap upon release.
  * **Long Press:** Pauses the bubble and notifies the user.

### 2. The "Chameleon" AR Screen Overlay (Google Lens Style)
* Samples background colors behind foreign text blocks directly from the screenshot.
* Renders softly rounded pills matching the original background color and selects high-contrast text color based on WCAG luminance calculations.
* **Auto-Fit Typography:** Binary-search text sizing dynamically scales font sizes to fit cleanly within original bounding boxes.
* **Top Floating Pill Controls:**
  * Shows language pair (`Auto [JA] → EN`) and engine mode (`⚡ Online Cloud` or `📴 Offline AI`).
  * **Hold to Peek:** Temporarily hides the translation pills to inspect original text/artwork.
  * **Tap Anywhere:** Dismisses the overlay in 150ms.

### 3. The Calm Dashboard (Jetpack Compose Material 3)
* **Master Power Card:** One-tap switch with 1-time Accessibility setup guidance.
* **Language & Smart OCR Card:** Auto-detection with script mode optimization (Auto All, Latin, Japanese, Chinese, Korean, Devanagari).
* **Offline Brain Manager:** 1-tap download and delete for on-device language models (~30 MB each) with airplane mode support.
* **Instant Screen Test Area:** Built-in multi-lingual test phrases (Japanese, French, Spanish) for immediate testing.

---

## 📂 Project Directory Structure

```text
TRANSLOY/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── xml/
        │   │   └── accessibility_service_config.xml
        │   └── values/
        │       ├── strings.xml
        │       └── themes.xml
        └── java/com/mustafoy/transloy/
            ├── MainActivity.kt
            ├── model/
            │   └── TranslationModels.kt
            ├── data/
            │   └── UserPreferences.kt
            ├── domain/
            │   ├── SmartOcrEngine.kt
            │   ├── LanguageDetectorEngine.kt
            │   └── HybridTranslationEngine.kt
            ├── overlay/
            │   ├── GhostBubbleView.kt
            │   └── ChameleonOverlayView.kt
            ├── service/
            │   └── TransloyAccessibilityService.kt
            └── ui/theme/
                └── Theme.kt
```

---

## 🛠️ Build & Installation Instructions

1. **Requirements:**
   * Android Studio Ladybug (2024.2.1) or newer
   * JDK 17
   * Android SDK (compileSdk 35, minSdk 30, targetSdk 35)

2. **Open & Sync:**
   * Open Android Studio -> **Open an Existing Project** -> Select the project root folder.
   * Allow Gradle to sync dependencies.

3. **Run on Device:**
   * Connect an Android 11+ physical device or start an emulator running API 30+.
   * Click **Run (Shift + F10)**.

4. **First-Time Activation:**
   * Open **TRANSLOY**.
   * Tap **"Enable 1-Time Screen Access"** -> Enable **TRANSLOY Screen Translator** in Android Accessibility settings -> Return to app.
   * The **Ghost Bubble** will appear on the screen edge. Tap it to translate foreign text instantly!

5. **Note on Android 13+ Side-loaded APKs:**
   * If installing via a raw `.apk` file instead of Android Studio or Google Play, Android 13+ may restrict Accessibility services.
   * Go to **Settings -> Apps -> TRANSLOY -> Top-right 3 dots -> "Allow restricted settings"**, then toggle the Accessibility service on.
   * Apps installed via Google Play Store do not have this restriction.

---

## 📄 License

Apache License 2.0. Built with Google ML Kit and Jetpack Compose.
