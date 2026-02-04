package display.interactive.whiteboard.state

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.MotionEvent

/**
 * Handles element erasure.
 */
class EraserState : ICanvasState {
    private val eraserIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 180
    }

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var hasErasedStart = false
    private var isCancelled = false

    override fun handleTouchEvent(event: MotionEvent, pointerId: Int, context: StateContext): Boolean {
        // Only process the first finger for eraser (V0.0.7)
        if (pointerId != 0) return false

        val x = context.toWorldX(event.getX(event.findPointerIndex(pointerId)))
        val y = context.toWorldY(event.getY(event.findPointerIndex(pointerId)))
        val uiState = context.sdk.uiState.value

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    downTime = System.currentTimeMillis()
                    downX = x
                    downY = y
                    hasErasedStart = false
                    isCancelled = false
                }
                
                // If zoom mode is disabled, erase immediately.
                // If enabled, we wait for MOVE or UP to confirm it's not a multi-touch gesture.
                if (!uiState.isZoomMode) {
                    context.sdk.eraseAt(x, y, 20f)
                    hasErasedStart = true
                }
                context.sdk.updateEraserPosition(PointF(x, y))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isCancelled) return false

                val currentTime = System.currentTimeMillis()
                // Threshold: 100ms delay to wait for potential second finger in Zoom/Pan mode
                if (!uiState.isZoomMode || (currentTime - downTime > 100L)) {
                    if (!hasErasedStart) {
                        context.sdk.eraseAt(downX, downY, 20f)
                        hasErasedStart = true
                    }
                    context.sdk.eraseAt(x, y, 20f)
                }
                context.sdk.updateEraserPosition(PointF(x, y))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isCancelled && event.actionMasked == MotionEvent.ACTION_UP) {
                    // Final check to erase the start point if it was a quick tap
                    if (!hasErasedStart) {
                        context.sdk.eraseAt(downX, downY, 20f)
                    }
                }
                context.sdk.updateEraserPosition(null)
                return true
            }
        }
        return false
    }

    override fun draw(canvas: Canvas, context: StateContext) {
        val uiState = context.sdk.uiState.value
        uiState.eraserPosition?.let { pos ->
            canvas.drawCircle(pos.x, pos.y, 20f, eraserIconPaint)
        }
    }

    override fun reset(context: StateContext) {
        isCancelled = true
        hasErasedStart = true // Prevent erasure on UP
        context.sdk.updateEraserPosition(null)
    }
}
