package display.interactive.whiteboard.state

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import display.interactive.accelerate.AccelerateAddr
import display.interactive.touch.HikDefaultTouchCalc
import display.interactive.touch.TouchType
import display.interactive.whiteboard.element.StrokeElement
import java.util.UUID
import kotlin.math.hypot

/**
 * Handles drawing strokes on the canvas.
 */
class PenState : ICanvasState {
    companion object {
        private const val TAG = "PenState"
        private const val BASE_ERASER_RADIUS = 20f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val activePaths = mutableMapOf<Int, Path>()
    private val activePoints = mutableMapOf<Int, MutableList<PointF>>()
    private val activeStrokeIds = mutableMapOf<Int, String>()
    private val lastRawPoints = mutableMapOf<Int, Pair<Float, Float>>()

    // Hand Eraser State
    private val handEraserMap = mutableMapOf<Int, Boolean>()
    private val lastEraserUpdate = mutableMapOf<Int, Long>()
    private val lastEraserPos = mutableMapOf<Int, PointF>()
    private val currentEraserRadii = mutableMapOf<Int, Float>()

    private val acceleratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val eraserIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 180
    }

    override fun handleTouchEvent(event: MotionEvent, pointerId: Int, context: StateContext): Boolean {
        val uiState = context.sdk.uiState.value
        
        // If not multi-finger enabled, only process events for the first finger (pointerId 0)
        if (!uiState.isMultiFingerEnabled && pointerId != 0) {
            return false
        }

        val index = event.findPointerIndex(pointerId)
        if (index == -1) return false

        val x = context.toWorldX(event.getX(index))
        val y = context.toWorldY(event.getY(index))
        val rawX = event.getRawX(index)
        val rawY = event.getRawY(index)
        val touchMajor = event.getTouchMajor(index)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionIndex == index) {
                    // Check for Hand Eraser
                    val touchType = HikDefaultTouchCalc.getTouchType(touchMajor)
                    if (touchType == TouchType.HandEraser) {
                        handEraserMap[pointerId] = true
                        lastEraserUpdate[pointerId] = System.currentTimeMillis()
                        lastEraserPos[pointerId] = PointF(rawX, rawY) // Use raw for speed calc
                        
                        // Initial radius based on touch major (pixel)
                        // Use transformMajor2Pixel to get accurate pixel size
                        val pixelSize = HikDefaultTouchCalc.transformMajor2Pixel(touchMajor)
                        val screenRadius = pixelSize / 2f
                        val worldRadius = screenRadius / context.scale
                        currentEraserRadii[pointerId] = worldRadius

                        context.sdk.eraseAt(x, y, worldRadius)
                        // We only update global eraser position for the primary pointer to avoid flickering
                        if (pointerId == 0) {
                            context.sdk.updateEraserPosition(PointF(x, y))
                        }
                        return true
                    }

                    handler.removeCallbacksAndMessages(null)
                    val path = Path().apply { moveTo(x, y) }
                    activePaths[pointerId] = path
                    activePoints[pointerId] = mutableListOf(PointF(x, y))
                    activeStrokeIds[pointerId] = UUID.randomUUID().toString()
                    
                    lastRawPoints[pointerId] = Pair(rawX, rawY)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Handle Hand Eraser
                if (handEraserMap[pointerId] == true) {
                    val currentTime = System.currentTimeMillis()
                    val lastTime = lastEraserUpdate[pointerId] ?: currentTime
                    val lastPos = lastEraserPos[pointerId] ?: PointF(rawX, rawY)
                    
                    val timeDelta = currentTime - lastTime
                    var speed = 0f
                    if (timeDelta > 0) {
                        val distance = hypot(rawX - lastPos.x, rawY - lastPos.y)
                        speed = distance / timeDelta // pixels per ms
                    }

                    // Dynamic sizing
                    // Base size from current touchMajor
                    val pixelSize = HikDefaultTouchCalc.transformMajor2Pixel(touchMajor)
                    val baseScreenRadius = pixelSize / 2f
                    // Speed factor: e.g. speed 2.0px/ms -> 2x size? 
                    // Let's say speed 5px/ms is fast.
                    // Heuristic: factor = 1 + speed * 0.5
                    val speedFactor = 1 + speed * 0.5f
                    val targetScreenRadius = baseScreenRadius * speedFactor
                    val worldRadius = targetScreenRadius / context.scale
                    
                    currentEraserRadii[pointerId] = worldRadius

                    context.sdk.eraseAt(x, y, worldRadius)
                    
                    lastEraserUpdate[pointerId] = currentTime
                    lastEraserPos[pointerId] = PointF(rawX, rawY)

                    if (pointerId == 0) {
                        context.sdk.updateEraserPosition(PointF(x, y))
                    }
                    return true
                }

                // Handle Writing (existing logic)
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    if (!uiState.isMultiFingerEnabled && pId != 0) continue
                    
                    // Skip if this pointer is an eraser
                    if (handEraserMap[pId] == true) continue

                    val pIndex = event.findPointerIndex(pId)
                    if (pIndex == -1) continue

                    val pX = context.toWorldX(event.getX(pIndex))
                    val pY = context.toWorldY(event.getY(pIndex))

                    activePaths[pId]?.lineTo(pX, pY)
                    activePoints[pId]?.add(PointF(pX, pY))

                    context.accelerateCanvas?.let { ac ->
                        acceleratePaint.color = uiState.currentStrokeColor
                        acceleratePaint.strokeWidth = uiState.currentStrokeWidth * context.scale
                        
                        val pRawX = event.getRawX(pIndex)
                        val pRawY = event.getRawY(pIndex)
                        
                        val lastRawPoint = lastRawPoints[pId]
                        if (lastRawPoint != null) {
                            ac.accelerateCanvas.drawLine(lastRawPoint.first, lastRawPoint.second, pRawX, pRawY, acceleratePaint)
                        }
                        lastRawPoints[pId] = Pair(pRawX, pRawY)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionIndex == index) {
                    if (handEraserMap[pointerId] == true) {
                        handEraserMap.remove(pointerId)
                        lastEraserUpdate.remove(pointerId)
                        lastEraserPos.remove(pointerId)
                        currentEraserRadii.remove(pointerId)
                        if (pointerId == 0) {
                            context.sdk.updateEraserPosition(null)
                        }
                        return true
                    }

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
        
        // Draw Hand Eraser indicators
        // We can check local state for multi-touch erasers, 
        // or rely on uiState.eraserPosition for the primary one.
        // Let's draw what we know locally to support multiple hands visual if needed
        // But also check uiState.eraserPosition for consistency if other states set it (though PenState is active)
        
        // Prioritize local eraser state for "Hand Eraser"
        if (handEraserMap.isNotEmpty()) {
             // We don't have the current position in a map easily accessible for draw() unless we stored it in ACTION_MOVE
             // But updateEraserPosition updates the UI state.
             // If we want to verify multi-hand erasure, we should store current pos in map.
             // For now, let's just rely on uiState.eraserPosition for the main one.
        }
        
        uiState.eraserPosition?.let { pos ->
            // If we are hand erasing, use the calculated radius for pointer 0 if available, else default
            val radius = currentEraserRadii[0] ?: BASE_ERASER_RADIUS
            canvas.drawCircle(pos.x, pos.y, radius, eraserIconPaint)
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
