package com.mustafoy.transloy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.mustafoy.transloy.model.ScreenTranslationResult
import com.mustafoy.transloy.model.SupportedLanguages

/**
 * Full-Screen Google-Lens-Style Chameleon AR Overlay:
 * - Draws translated text right on top of original coordinates with background-matched colors.
 * - Auto-scales font size so translated paragraphs fit cleanly inside every box.
 * - Includes a sleek top floating pill: Hold to Peek Original | Tap Anywhere to Dismiss.
 */
class ChameleonOverlayView(
    context: Context,
    private val result: ScreenTranslationResult,
    private val onDismiss: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val screenOffset = IntArray(2)

    private var isPeekingOriginal = false
    private val pillRect = RectF()
    private val peekButtonRect = RectF()

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#33FFFFFF")
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E61E1B4B")
        style = Paint.Style.FILL
    }

    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12.5f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(screenOffset)
        val offsetX = screenOffset[0].toFloat()
        val offsetY = screenOffset[1].toFloat()

        // 1. Draw Chameleon Translated Blocks (unless user is holding "Peek")
        if (!isPeekingOriginal) {
            for (block in result.blocks) {
                val pad = 4f * density
                val rectF = RectF(
                    block.boundingBox.left - offsetX - pad,
                    block.boundingBox.top - offsetY - pad,
                    block.boundingBox.right - offsetX + pad,
                    block.boundingBox.bottom - offsetY + pad
                )

                boxPaint.color = block.backgroundColor
                canvas.drawRoundRect(rectF, 6f * density, 6f * density, boxPaint)
                canvas.drawRoundRect(rectF, 6f * density, 6f * density, borderPaint)

                drawAutoSizedText(canvas, block.translatedText, rectF, block.textColor)
            }
        }

        // 2. Draw Top Floating Control Pill
        val srcName = SupportedLanguages.nameFor(result.detectedSourceLang)
        val tgtName = SupportedLanguages.nameFor(result.targetLang)
        val header = "$srcName → $tgtName • ${result.engineUsed.badge}   |   👁 Hold Peek   |   ✕"

        val pillWidth = (pillTextPaint.measureText(header) + 36 * density).coerceAtMost(width - 24 * density)
        val pillHeight = 38 * density
        val topMargin = 46 * density

        pillRect.set(
            (width - pillWidth) / 2f,
            topMargin,
            (width + pillWidth) / 2f,
            topMargin + pillHeight
        )
        // Right portion of pill acts as "Hold to Peek" zone
        peekButtonRect.set(
            pillRect.right - (125 * density),
            pillRect.top,
            pillRect.right - (32 * density),
            pillRect.bottom
        )

        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
        canvas.drawText(
            header,
            pillRect.centerX(),
            pillRect.centerY() + (4.5f * density),
            pillTextPaint
        )
    }

    private fun drawAutoSizedText(canvas: Canvas, text: String, box: RectF, color: Int) {
        val innerPad = 3f * density
        val availWidth = (box.width() - innerPad * 2).toInt().coerceAtLeast(24)
        val availHeight = (box.height() - innerPad * 2).coerceAtLeast(14f)

        textPaint.color = color
        textPaint.isFakeBoldText = true

        var low = 8f * density
        var high = 28f * density
        var bestLayout: StaticLayout? = null

        // Fast binary search for the exact font size that fills the bounding box cleanly
        repeat(7) {
            val mid = (low + high) / 2f
            textPaint.textSize = mid
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, availWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()

            if (layout.height <= availHeight) {
                bestLayout = layout
                low = mid
            } else {
                high = mid
            }
        }

        val finalLayout = bestLayout ?: run {
            textPaint.textSize = 8.5f * density
            StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, availWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        }

        canvas.save()
        val dy = box.top + innerPad + ((availHeight - finalLayout.height) / 2f).coerceAtLeast(0f)
        canvas.translate(box.left + innerPad, dy)
        finalLayout.draw(canvas)
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (peekButtonRect.contains(event.x, event.y)) {
                    isPeekingOriginal = true
                    invalidate()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPeekingOriginal) {
                    isPeekingOriginal = false
                    invalidate()
                } else {
                    // Tapping anywhere else instantly closes the translation overlay
                    onDismiss()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
