package display.interactive.whiteboard.impl

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import display.interactive.accelerate.AccelerateAddr
import display.interactive.accelerate.AccelerateCanvas
import display.interactive.whiteboard.element.*
import java.util.UUID

/**
 * Handles touch events and translates them into whiteboard actions.
 */
class TouchHandler(private val viewModel: WhiteBoardSDKImpl) {

    private val handler = Handler(Looper.getMainLooper())
    private var accelerateCanvas: AccelerateCanvas? = null
    private var selectedHandler: SelectedHandler? = null

    private val acceleratePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    // Track multiple fingers for drawing
    val activePaths = mutableMapOf<Int, Path>()
    private val activePoints = mutableMapOf<Int, MutableList<PointF>>()
    private val activeStrokeIds = mutableMapOf<Int, String>()
    private val lastPoints = mutableMapOf<Int, Pair<Float, Float>>()

    fun setAccelerateCanvas(canvas: AccelerateCanvas) {
        this.accelerateCanvas = canvas
    }

    fun setSelectedHandler(handler: SelectedHandler) {
        this.selectedHandler = handler
    }

    // For element manipulation (V0.0.2)
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false

    // For Lasso Selection (V0.0.3)
    var selectionRect: RectF? = null
    private var isSelecting = false
    private var selectionStartX = 0f
    private var selectionStartY = 0f

    fun handleTouchEvent(event: MotionEvent, scale: Float, offsetX: Float, offsetY: Float): Boolean {
        val uiState = viewModel.uiState.value
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        // Convert screen coordinates to world coordinates
        val x = (event.getX(actionIndex) - offsetX) / scale
        val y = (event.getY(actionIndex) - offsetY) / scale

        when (uiState.interactionMode) {
            InteractionMode.DRAW -> return handleDrawEvent(event, pointerId, x, y, uiState)
            InteractionMode.SELECT -> return handleSelectEvent(event, pointerId, x, y, uiState)
            InteractionMode.ERASER -> return handleEraserEvent(event, pointerId, x, y, uiState)
            InteractionMode.NAVIGATE -> return false
        }
    }

    private fun handleDrawEvent(event: MotionEvent, pointerId: Int, x: Float, y: Float, state: WhiteBoardState): Boolean {
        if (event.pointerCount > 1 && !state.isMultiFingerEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                handler.removeCallbacksAndMessages(null) // Cancel any pending clear
                val path = Path().apply { moveTo(x, y) }
                activePaths[pointerId] = path
                activePoints[pointerId] = mutableListOf(PointF(x, y))
                activeStrokeIds[pointerId] = UUID.randomUUID().toString()
                lastPoints[pointerId] = Pair(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val pX = (event.getX(i) - state.canvasOffsetX) / state.canvasScale
                    val pY = (event.getY(i) - state.canvasOffsetY) / state.canvasScale

                    activePaths[pId]?.lineTo(pX, pY)
                    activePoints[pId]?.add(PointF(pX, pY))

                    // Draw on accelerate layer
                    accelerateCanvas?.let { ac ->
                        acceleratePaint.color = state.currentStrokeColor
                        acceleratePaint.strokeWidth = state.currentStrokeWidth
                        val lastPoint = lastPoints[pId]
                        if (lastPoint != null) {
                            ac.accelerateCanvas.drawLine(lastPoint.first, lastPoint.second, pX, pY, acceleratePaint)
                        }
                    }
                    lastPoints[pId] = Pair(pX, pY)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val points = activePoints[pointerId]
                if (points != null && points.isNotEmpty()) {
                    // Find top-left to make points relative
                    val minX = points.minOf { it.x }
                    val minY = points.minOf { it.y }
                    val relativePoints = points.map { PointF(it.x - minX, it.y - minY) }

                    val stroke = StrokeElement(
                        id = activeStrokeIds[pointerId] ?: UUID.randomUUID().toString(),
                        points = relativePoints,
                        color = state.currentStrokeColor,
                        strokeWidth = state.currentStrokeWidth,
                        type = state.currentStrokeType,
                        zIndex = state.elements.size
                    )
                    stroke.x = minX
                    stroke.y = minY
                    viewModel.addElement(stroke)
                }
                activePaths.remove(pointerId)
                activePoints.remove(pointerId)
                activeStrokeIds.remove(pointerId)
                lastPoints.remove(pointerId)

                // If all fingers are up, clear accelerate layer after 300ms
                if (activePaths.isEmpty()) {
                    handler.postDelayed({
                        AccelerateAddr.clearVopByJar()
                    }, 300)
                }
                return true
            }
        }
        return false
    }

    private fun handleEraserEvent(event: MotionEvent, pointerId: Int, x: Float, y: Float, state: WhiteBoardState): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Perform erasure. Eraser radius can be hardcoded or based on stroke width.
                viewModel.eraseAt(x, y, 20f)
                viewModel.updateEraserPosition(PointF(x, y))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                viewModel.updateEraserPosition(null)
                return true
            }
        }
        return false
    }

    private fun handleSelectEvent(event: MotionEvent, pointerId: Int, x: Float, y: Float, state: WhiteBoardState): Boolean {
        // First, let SelectedHandler handle events if something is already selected
        if (state.selectedElementIds.isNotEmpty()) {
            if (selectedHandler?.handleTouchEvent(event, state.elements, state.selectedElementIds, x, y) == true) {
                return true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                val elements = state.elements
                val hitElement = elements.asReversed().find { it.contains(x, y) }

                if (hitElement != null) {
                    // If hit element is NOT already selected, select only this one
                    if (hitElement.id !in state.selectedElementIds) {
                        viewModel.selectElement(hitElement.id)
                    }
                    // Start dragging the selection (whether it was already selected or just got selected)
                    isDragging = true
                    isSelecting = false
                } else {
                    // Start Lasso Selection
                    viewModel.deselectAll()
                    isSelecting = true
                    isDragging = false
                    selectionStartX = x
                    selectionStartY = y
                    selectionRect = RectF(x, y, x, y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = x - lastX
                    val dy = y - lastY
                    viewModel.moveSelectedElements(dx, dy)
                    lastX = x
                    lastY = y
                    return true
                } else if (isSelecting) {
                    selectionRect = RectF(
                        minOf(selectionStartX, x),
                        minOf(selectionStartY, y),
                        maxOf(selectionStartX, x),
                        maxOf(selectionStartY, y)
                    )
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    viewModel.commitMove()
                } else if (isSelecting && selectionRect != null) {
                    val rect = selectionRect!!
                    val selectedIds = state.elements.filter { element ->
                        RectF.intersects(rect, element.getBounds())
                    }.map { it.id }.toSet()

                    if (selectedIds.isNotEmpty()) {
                        selectedIds.forEach { viewModel.selectElement(it, multiSelect = true) }
                    }
                }
                isDragging = false
                isSelecting = false
                selectionRect = null
                return true
            }
        }
        return false
    }
}
