package display.interactive.whiteboard.state

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import display.interactive.accelerate.AccelerateAddr
import display.interactive.whiteboard.element.StrokeElement
import java.util.UUID

/**
 * Handles drawing strokes on the canvas.
 */
class PenState : ICanvasState {
    companion object {
        private const val TAG = "PenState"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val activePaths = mutableMapOf<Int, Path>()
    private val activePoints = mutableMapOf<Int, MutableList<PointF>>()
    private val activeStrokeIds = mutableMapOf<Int, String>()
    private val lastRawPoints = mutableMapOf<Int, Pair<Float, Float>>()

    private val acceleratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun handleTouchEvent(event: MotionEvent, pointerId: Int, context: StateContext): Boolean {
        val uiState = context.sdk.uiState.value
        
        // If not multi-finger enabled, only process events for the first finger (pointerId 0)
        if (!uiState.isMultiFingerEnabled && pointerId != 0) {
            return false
        }

        val x = context.toWorldX(event.getX(event.findPointerIndex(pointerId)))
        val y = context.toWorldY(event.getY(event.findPointerIndex(pointerId)))

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionIndex == event.findPointerIndex(pointerId)) {
                    handler.removeCallbacksAndMessages(null)
                    val path = Path().apply { moveTo(x, y) }
                    activePaths[pointerId] = path
                    activePoints[pointerId] = mutableListOf(PointF(x, y))
                    activeStrokeIds[pointerId] = UUID.randomUUID().toString()
                    
                    val index = event.findPointerIndex(pointerId)
                    lastRawPoints[pointerId] = Pair(event.getRawX(index), event.getRawY(index))
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    if (!uiState.isMultiFingerEnabled && pId != 0) continue
                    
                    val pX = context.toWorldX(event.getX(i))
                    val pY = context.toWorldY(event.getY(i))

                    activePaths[pId]?.lineTo(pX, pY)
                    activePoints[pId]?.add(PointF(pX, pY))

                    context.accelerateCanvas?.let { ac ->
                        acceleratePaint.color = uiState.currentStrokeColor
                        acceleratePaint.strokeWidth = uiState.currentStrokeWidth * context.scale
                        
                        val rawX = event.getRawX(i)
                        val rawY = event.getRawY(i)
                        
                        val lastRawPoint = lastRawPoints[pId]
                        if (lastRawPoint != null) {
                            ac.accelerateCanvas.drawLine(lastRawPoint.first, lastRawPoint.second, rawX, rawY, acceleratePaint)
                        }
                        lastRawPoints[pId] = Pair(rawX, rawY)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionIndex == event.findPointerIndex(pointerId)) {
                    val points = activePoints[pointerId]
                    if (points != null && points.isNotEmpty()) {
                        val minX = points.minOf { it.x }
                        val minY = points.minOf { it.y }
                        val relativePoints = points.map { PointF(it.x - minX, it.y - minY) }

                        val stroke = StrokeElement(
                            id = activeStrokeIds[pointerId] ?: UUID.randomUUID().toString(),
                            points = relativePoints,
                            color = uiState.currentStrokeColor,
                            strokeWidth = uiState.currentStrokeWidth,
                            type = uiState.currentStrokeType,
                            zIndex = uiState.elements.size
                        )
                        stroke.x = minX
                        stroke.y = minY
                        context.sdk.addElement(stroke)
                    }
                    activePaths.remove(pointerId)
                    activePoints.remove(pointerId)
                    activeStrokeIds.remove(pointerId)
                    lastRawPoints.remove(pointerId)

                    if (activePaths.isEmpty()) {
                        handler.postDelayed({
                            AccelerateAddr.clearVopByJar()
                        }, 300)
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun draw(canvas: Canvas, context: StateContext) {
        val uiState = context.sdk.uiState.value
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = uiState.currentStrokeColor
            strokeWidth = uiState.currentStrokeWidth
        }

        when (uiState.currentStrokeType) {
            StrokeElement.StrokeType.PEN -> paint.alpha = 255
            StrokeElement.StrokeType.PENCIL -> paint.alpha = 200
            StrokeElement.StrokeType.MARKER -> paint.alpha = 128
        }

        activePaths.values.forEach { path ->
            canvas.drawPath(path, paint)
        }
    }

    override fun reset(context: StateContext) {
        clearActivePaths()
    }

    fun clearActivePaths() {
        activePaths.clear()
        activePoints.clear()
        activeStrokeIds.clear()
        lastRawPoints.clear()
        AccelerateAddr.clearVopByJar()
    }
}
