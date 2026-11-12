package com.mustafoy.transloy.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * Non-intrusive "Ghost Edge" Floating Bubble:
 * - Active when touched (100% crisp visibility).
 * - After 3 seconds idle: docks smoothly to nearest screen edge, tucks 38% into bezel,
 *   and fades to 38% ghost opacity so it NEVER disturbs reading, typing, or gaming.
 */
class GhostBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onTapTranslate: () -> Unit,
    private val onLongPressHide: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    val bubbleSizePx = (52 * density).toInt()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F46E5") // Calm Indigo
        style = Paint.Style.FILL
        setShadowLayer(8 * density, 0f, 3 * density, Color.parseColor("#55000000"))
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34D399") // Mint Emerald status ring
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18 * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val idleHandler = Handler(Looper.getMainLooper())
    private var isLoading = false
    private var isDockedGhost = false
    private var pulseFraction = 0f
    private var pulseAnimator: ValueAnimator? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var startParamX = 0
    private var startParamY = 0
    private var longPressTriggered = false

    private val ghostRunnable = Runnable { enterGhostMode() }
    private val longPressRunnable = Runnable {
        longPressTriggered = true
        onLongPressHide()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        scheduleGhostMode()
    }

    fun setLoadingState(loading: Boolean) {
        isLoading = loading
        if (loading) {
            wakeFromGhostMode()
            idleHandler.removeCallbacks(ghostRunnable)
            pulseAnimator?.cancel()
            pulseAnimator = ValueAnimator.ofFloat(0.2f, 1f).apply {
                duration = 650
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    pulseFraction = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            pulseAnimator?.cancel()
            pulseFraction = 0f
            invalidate()
            scheduleGhostMode()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width / 2f) - (6 * density)

        canvas.drawCircle(cx, cy, radius, bgPaint)

        if (isLoading) {
            ringPaint.alpha = (pulseFraction * 255).toInt()
            canvas.drawCircle(cx, cy, radius + (2 * density * pulseFraction), ringPaint)
            canvas.drawText("•••", cx, cy + (6 * density), textPaint)
        } else {
            ringPaint.alpha = 220
            canvas.drawCircle(cx, cy, radius, ringPaint)
            canvas.drawText("文A", cx, cy + (6 * density), textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLoading) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                wakeFromGhostMode()
                idleHandler.removeCallbacks(ghostRunnable)
                longPressTriggered = false
                idleHandler.postDelayed(longPressRunnable, 750)

                downRawX = event.rawX
                downRawY = event.rawY
                startParamX = layoutParams.x
                startParamY = layoutParams.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) > 10 * density || abs(dy) > 10 * density) {
                    idleHandler.removeCallbacks(longPressRunnable)
                }
                layoutParams.x = (startParamX + dx).toInt()
                layoutParams.y = (startParamY + dy).toInt()
                safeUpdateView()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                idleHandler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered) {
                    val movedDist = abs(event.rawX - downRawX) + abs(event.rawY - downRawY)
                    if (movedDist < 12 * density) {
                        onTapTranslate()
                    } else {
                        snapToNearestEdge()
                        scheduleGhostMode()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToNearestEdge(tuckIntoBezel: Boolean = false) {
        val screenWidth = resources.displayMetrics.widthPixels
        val isLeft = (layoutParams.x + bubbleSizePx / 2) < (screenWidth / 2)
        val tuckOffset = if (tuckIntoBezel) (bubbleSizePx * 0.38f).toInt() else 0
        val targetX = if (isLeft) -tuckOffset else (screenWidth - bubbleSizePx + tuckOffset)

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                layoutParams.x = it.animatedValue as Int
                safeUpdateView()
            }
            start()
        }
    }

    private fun scheduleGhostMode() {
        idleHandler.removeCallbacks(ghostRunnable)
        idleHandler.postDelayed(ghostRunnable, 3000)
    }

    private fun enterGhostMode() {
        isDockedGhost = true
        animate().alpha(0.38f).setDuration(280).start()
        snapToNearestEdge(tuckIntoBezel = true)
    }

    private fun wakeFromGhostMode() {
        if (isDockedGhost) {
            isDockedGhost = false
            animate().alpha(1f).setDuration(150).start()
            snapToNearestEdge(tuckIntoBezel = false)
        } else {
            alpha = 1f
        }
    }

    private fun safeUpdateView() {
        if (isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(this, layoutParams) }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idleHandler.removeCallbacksAndMessages(null)
        pulseAnimator?.cancel()
    }
}
