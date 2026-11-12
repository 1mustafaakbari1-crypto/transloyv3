package com.mustafoy.transloy.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import com.mustafoy.transloy.data.AppSettings
import com.mustafoy.transloy.data.UserPreferences
import com.mustafoy.transloy.domain.HybridTranslationEngine
import com.mustafoy.transloy.domain.LanguageDetectorEngine
import com.mustafoy.transloy.domain.SmartOcrEngine
import com.mustafoy.transloy.model.ScreenTranslationResult
import com.mustafoy.transloy.model.TranslatedBlock
import com.mustafoy.transloy.overlay.ChameleonOverlayView
import com.mustafoy.transloy.overlay.GhostBubbleView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TransloyAccessibilityService : AccessibilityService() {

    companion object {
        val isServiceConnected = MutableStateFlow(false)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: UserPreferences
    private lateinit var ocrEngine: SmartOcrEngine
    private lateinit var languageDetector: LanguageDetectorEngine
    private lateinit var translationEngine: HybridTranslationEngine

    private var currentSettings = AppSettings()
    private var bubbleView: GhostBubbleView? = null
    private var activeOverlayView: ChameleonOverlayView? = null
    private var isBusyTranslating = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected.value = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = UserPreferences(applicationContext)
        ocrEngine = SmartOcrEngine()
        languageDetector = LanguageDetectorEngine()
        translationEngine = HybridTranslationEngine(applicationContext)

        serviceScope.launch {
            prefs.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                if (settings.bubbleEnabled) {
                    showBubbleIfNeeded()
                } else {
                    removeOverlay()
                    removeBubble()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive only — zero background CPU usage and zero privacy intrusion!
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isServiceConnected.value = false
        removeOverlay()
        removeBubble()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showBubbleIfNeeded() {
        if (bubbleView != null) return

        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx
            y = (resources.displayMetrics.heightPixels * 0.38f).toInt()
        }

        val view = GhostBubbleView(
            context = this,
            windowManager = windowManager,
            layoutParams = params,
            onTapTranslate = { triggerScreenTranslation() },
            onLongPressHide = {
                serviceScope.launch {
                    prefs.setBubbleEnabled(false)
                    Toast.makeText(
                        applicationContext,
                        "TRANSLOY Bubble Paused. Re-enable anytime inside app.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        runCatching {
            windowManager.addView(view, params)
            bubbleView = view
        }
    }

    private fun removeBubble() {
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
        }
        bubbleView = null
    }

    private fun removeOverlay() {
        activeOverlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        activeOverlayView = null
    }

    private fun triggerScreenTranslation() {
        if (isBusyTranslating) return
        isBusyTranslating = true

        serviceScope.launch {
            try {
                // 1. Hide existing overlay & Ghost Bubble cleanly before taking screenshot
                removeOverlay()
                bubbleView?.visibility = View.INVISIBLE
                delay(75) // Wait 4 frames so SurfaceFlinger renders screen without bubble

                // 2. Capture clean software Bitmap via Accessibility Screenshot API
                val screenshot = captureSoftwareScreenshot()
                bubbleView?.visibility = View.VISIBLE
                bubbleView?.setLoadingState(true)

                if (screenshot == null) {
                    Toast.makeText(
                        applicationContext,
                        "Please wait a moment before capturing again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                try {
                    // 3. Multi-script OCR extraction
                    val ocrBlocks = ocrEngine.extractTextBlocks(screenshot, currentSettings.ocrScriptMode)
                    if (ocrBlocks.isEmpty()) {
                        Toast.makeText(
                            applicationContext,
                            "No foreign text detected on screen.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // 4. Auto-detect source language from combined screen text
                    val combinedSample = ocrBlocks.joinToString(" ") { it.originalText }
                    val detectedSource = languageDetector.detectLanguageCode(combinedSample)
                    val targetLang = currentSettings.targetLanguage

                    // 5. Hybrid Online/Offline Translation
                    val (translatedStrings, engineUsed) = translationEngine.translateBatch(
                        texts = ocrBlocks.map { it.originalText },
                        sourceLangCode = detectedSource,
                        targetLangCode = targetLang,
                        autoDownloadOffline = currentSettings.autoDownloadOfflinePacks
                    )

                    // 6. Sample Chameleon background colors per block
                    val finalBlocks = ocrBlocks.mapIndexed { index, block ->
                        val bgColor = sampleBackgroundColor(screenshot, block.boundingBox)
                        val txtColor = pickHighContrastTextColor(bgColor)
                        TranslatedBlock(
                            originalText = block.originalText,
                            translatedText = translatedStrings.getOrElse(index) { block.originalText },
                            boundingBox = block.boundingBox,
                            backgroundColor = bgColor,
                            textColor = txtColor
                        )
                    }

                    // 7. Display Full-Screen Chameleon AR Overlay
                    showChameleonOverlay(
                        ScreenTranslationResult(
                            blocks = finalBlocks,
                            detectedSourceLang = detectedSource,
                            targetLang = targetLang,
                            engineUsed = engineUsed
                        )
                    )
                } finally {
                    screenshot.recycle()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    applicationContext,
                    "Translation error: ${e.localizedMessage ?: "Try again"}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                bubbleView?.visibility = View.VISIBLE
                bubbleView?.setLoadingState(false)
                isBusyTranslating = false
            }
        }
    }

    private suspend fun captureSoftwareScreenshot(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val buffer = screenshotResult.hardwareBuffer
                        val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.colorSpace)
                        // Convert Config.HARDWARE to ARGB_8888 so pixel sampling never crashes
                        val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hwBitmap?.recycle()
                        buffer.close()
                        if (cont.isActive) cont.resume(swBitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        }

    private fun sampleBackgroundColor(bitmap: Bitmap, rect: Rect): Int {
        val w = bitmap.width
        val h = bitmap.height
        val points = listOf(
            (rect.left - 3).coerceIn(0, w - 1) to rect.centerY().coerceIn(0, h - 1),
            (rect.right + 3).coerceIn(0, w - 1) to rect.centerY().coerceIn(0, h - 1),
            rect.centerX().coerceIn(0, w - 1) to (rect.top - 3).coerceIn(0, h - 1),
            rect.centerX().coerceIn(0, w - 1) to (rect.bottom + 3).coerceIn(0, h - 1)
        )

        var rSum = 0
        var gSum = 0
        var bSum = 0
        for ((x, y) in points) {
            val pixel = bitmap.getPixel(x, y)
            rSum += Color.red(pixel)
            gSum += Color.green(pixel)
            bSum += Color.blue(pixel)
        }
        val count = points.size
        // 94% opacity solid background pill so original foreign text underneath is cleanly hidden
        return Color.argb(242, rSum / count, gSum / count, bSum / count)
    }

    private fun pickHighContrastTextColor(bgColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(
            Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        )
        return if (luminance > 0.48) Color.parseColor("#111827") else Color.WHITE
    }

    private fun showChameleonOverlay(result: ScreenTranslationResult) {
        removeOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val overlay = ChameleonOverlayView(
            context = this,
            result = result,
            onDismiss = { removeOverlay() }
        )

        runCatching {
            windowManager.addView(overlay, params)
            activeOverlayView = overlay
        }
    }
}
