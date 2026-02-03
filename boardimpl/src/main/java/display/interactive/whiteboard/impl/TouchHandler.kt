package display.interactive.whiteboard.impl

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import display.interactive.accelerate.AccelerateAddr
import display.interactive.accelerate.AccelerateCanvas
import display.interactive.whiteboard.element.*
import java.util.UUID

/**
 * Handles touch events and translates them into whiteboard actions.
 */
class TouchHandler(private val viewModel: WhiteBoardSDKImpl) {
    
    companion object {
        private const val TAG = "TouchHandler"
    }

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
    
    // For Pan and Zoom (V0.0.7)
    private var lastZoomDistance = 0f
    private var lastZoomCenterX = 0f
    private var lastZoomCenterY = 0f
    
    // V0.0.7 Optimization: State and Thresholds
    private var isZooming = false // Currently scaling
    private var isPanning = false // Currently moving canvas
    private var initialPointersTime = 0L // Timestamp of first pointer
    private val POINTER_TIMEOUT = 100L // Threshold for "simultaneous" touch (ms)
    private val ZOOM_THRESHOLD = 10f // Threshold for scale change (pixels)
    private val PAN_THRESHOLD = 5f // Threshold for pan change (pixels)
    private var startZoomDistance = 0f // Distance when 2nd finger went down
    private var startZoomCenterX = 0f
    private var startZoomCenterY = 0f

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

    // For Lasso Selection (V0.0.6)
    var selectionPath: Path? = null
    private var isSelecting = false
    private var isLassoSelected = false // Track if selection came from lasso

    fun isLassoSelected(): Boolean = isLassoSelected

    fun handleTouchEvent(event: MotionEvent, scale: Float, offsetX: Float, offsetY: Float): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_MOVE) {
            Log.d(TAG, "handleTouchEvent: action=${event.actionMasked}, pointers=${event.pointerCount}")
        }
        val uiState = viewModel.uiState.value
        
        // Track time for simultaneous touch detection
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "handleTouchEvent: ACTION_DOWN, recording initialPointersTime")
            initialPointersTime = System.currentTimeMillis()
        }

        // Handle Pan and Zoom if enabled and 2+ fingers are down (V0.0.7)
        if (uiState.isZoomMode && event.pointerCount >= 2) {
            // Check if pointers arrived "simultaneously" to avoid stealing an ongoing drawing
            val currentTime = System.currentTimeMillis()
            val isSimultaneous = currentTime - initialPointersTime < POINTER_TIMEOUT
            
            // If we are already zooming/panning, or if it's a simultaneous multi-touch, enter Zoom/Pan logic
            if (isSimultaneous || isZooming || isPanning) {
                if (event.actionMasked != MotionEvent.ACTION_MOVE) {
                     Log.d(TAG, "handleTouchEvent: diverting to handleZoomPanEvent (simultaneous=$isSimultaneous, zooming=$isZooming, panning=$isPanning)")
                }
                return handleZoomPanEvent(event, uiState)
            }
            // Otherwise (e.g., first finger writing, second finger comes down later), 
            // do NOT interrupt the first finger. We fall through to process drawing/etc.
            if (event.actionMasked != MotionEvent.ACTION_MOVE) {
                Log.d(TAG, "handleTouchEvent: ignoring 2nd finger for zoom/pan (not simultaneous)")
            }
        }

        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        // Convert screen coordinates to world coordinates
        val x = (event.getX(actionIndex) - offsetX) / scale
        val y = (event.getY(actionIndex) - offsetY) / scale

        when (uiState.interactionMode) {
            InteractionMode.DRAW -> return handleDrawEvent(event, pointerId, x, y, uiState)
            InteractionMode.SELECT -> {
                // Only process the first finger for selection (V0.0.7)
                if (pointerId != 0) return false
                return handleSelectEvent(event, pointerId, x, y, uiState)
            }
            InteractionMode.ERASER -> {
                // Only process the first finger for eraser (V0.0.7)
                if (pointerId != 0) return false
                return handleEraserEvent(event, pointerId, x, y, uiState)
            }
            InteractionMode.NAVIGATE -> return false
        }
    }

    /**
     * Handles zooming and panning of the canvas.
     */
    private fun handleZoomPanEvent(event: MotionEvent, state: WhiteBoardState): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    Log.d(TAG, "handleZoomPanEvent: ACTION_POINTER_DOWN, starting zoom/pan detection")
                    // Cancel any active drawing paths when starting to zoom/pan
                    activePaths.clear()
                    activePoints.clear()
                    activeStrokeIds.clear()
                    lastPoints.clear()
                    AccelerateAddr.clearVopByJar()

                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    startZoomDistance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    startZoomCenterX = (event.getX(0) + event.getX(1)) / 2
                    startZoomCenterY = (event.getY(0) + event.getY(1)) / 2
                    
                    lastZoomDistance = startZoomDistance
                    lastZoomCenterX = startZoomCenterX
                    lastZoomCenterY = startZoomCenterY
                    
                    isZooming = false
                    isPanning = false
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val centerX = (event.getX(0) + event.getX(1)) / 2
                    val centerY = (event.getY(0) + event.getY(1)) / 2

                    // Exclusive decision: If not already zooming/panning, decide which one to start
                    if (!isZooming && !isPanning) {
                        val distDelta = Math.abs(distance - startZoomDistance)
                        val panDeltaX = Math.abs(centerX - startZoomCenterX)
                        val panDeltaY = Math.abs(centerY - startZoomCenterY)
                        
                        if (distDelta > ZOOM_THRESHOLD) {
                            Log.d(TAG, "handleZoomPanEvent: locked to ZOOM mode (delta=$distDelta)")
                            isZooming = true
                        } else if (panDeltaX > PAN_THRESHOLD || panDeltaY > PAN_THRESHOLD) {
                            Log.d(TAG, "handleZoomPanEvent: locked to PAN mode (deltaX=$panDeltaX, deltaY=$panDeltaY)")
                            isPanning = true
                        }
                    }

                    // Execute based on locked state
                    if (isZooming) {
                        if (lastZoomDistance > 0) {
                            val scaleFactor = distance / lastZoomDistance
                            val newScale = (state.canvasScale * scaleFactor).coerceIn(0.1f, 10f)
                            
                            val actualScaleFactor = newScale / state.canvasScale
                            val newOffsetX = centerX - (centerX - state.canvasOffsetX) * actualScaleFactor
                            val newOffsetY = centerY - (centerY - state.canvasOffsetY) * actualScaleFactor
                            
                            viewModel.setCanvasTransform(newScale, newOffsetX, newOffsetY)
                        }
                    } else if (isPanning) {
                        val panX = centerX - lastZoomCenterX
                        val panY = centerY - lastZoomCenterY
                        viewModel.setCanvasTransform(state.canvasScale, state.canvasOffsetX + panX, state.canvasOffsetY + panY)
                    }

                    lastZoomDistance = distance
                    lastZoomCenterX = centerX
                    lastZoomCenterY = centerY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    Log.d(TAG, "handleZoomPanEvent: ACTION_UP/POINTER_UP, resetting zoom/pan state")
                    lastZoomDistance = 0f
                    isZooming = false
                    isPanning = false
                }
                return true
            }
        }
        return false
    }

    private fun handleDrawEvent(event: MotionEvent, pointerId: Int, x: Float, y: Float, state: WhiteBoardState): Boolean {
        // If not multi-finger enabled, only process events for the first finger (pointerId 0)
        if (!state.isMultiFingerEnabled && pointerId != 0) {
            if (event.actionMasked != MotionEvent.ACTION_MOVE) {
                Log.d(TAG, "handleDrawEvent: ignoring pointer $pointerId (multi-finger disabled)")
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                Log.d(TAG, "handleDrawEvent: ACTION_DOWN/POINTER_DOWN for pointer $pointerId")
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
                    
                    // If not multi-finger enabled, ignore pointers other than 0
                    if (!state.isMultiFingerEnabled && pId != 0) continue
                    
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
                // If not multi-finger enabled, ignore pointers other than 0
                if (!state.isMultiFingerEnabled && pointerId != 0) return false
                
                Log.d(TAG, "handleDrawEvent: ACTION_UP/POINTER_UP for pointer $pointerId")
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
                    Log.d(TAG, "handleDrawEvent: stroke added to viewModel, points count=${relativePoints.size}")
                }
                activePaths.remove(pointerId)
                activePoints.remove(pointerId)
                activeStrokeIds.remove(pointerId)
                lastPoints.remove(pointerId)

                // If all fingers are up, clear accelerate layer after 300ms
                if (activePaths.isEmpty()) {
                    Log.d(TAG, "handleDrawEvent: all paths removed, scheduling accelerate layer clear")
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
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                Log.d(TAG, "handleEraserEvent: ACTION_DOWN/POINTER_DOWN for pointer $pointerId")
                viewModel.eraseAt(x, y, 20f)
                viewModel.updateEraserPosition(PointF(x, y))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Perform erasure. Eraser radius can be hardcoded or based on stroke width.
                viewModel.eraseAt(x, y, 20f)
                viewModel.updateEraserPosition(PointF(x, y))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                Log.d(TAG, "handleEraserEvent: ACTION_UP/CANCEL for pointer $pointerId")
                viewModel.updateEraserPosition(null)
                return true
            }
        }
        return false
    }

    private fun handleSelectEvent(event: MotionEvent, pointerId: Int, x: Float, y: Float, state: WhiteBoardState): Boolean {
        // First, let SelectedHandler handle events if something is already selected via lasso
        if (state.selectedElementIds.isNotEmpty() && isLassoSelected) {
            if (selectedHandler?.handleTouchEvent(event, state.elements, state.selectedElementIds, x, y) == true) {
                if (event.actionMasked != MotionEvent.ACTION_MOVE) {
                    Log.d(TAG, "handleSelectEvent: SelectedHandler consumed event ${event.actionMasked}")
                }
                return true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "handleSelectEvent: ACTION_DOWN at ($x, $y)")
                lastX = x
                lastY = y
                val elements = state.elements
                val hitElement = elements.asReversed().find { it.contains(x, y) }

                if (hitElement != null) {
                    Log.d(TAG, "handleSelectEvent: hit element ${hitElement.id}")
                    // If hit element is NOT already selected, select only this one
                    if (hitElement.id !in state.selectedElementIds) {
                        viewModel.selectElement(hitElement.id)
                    }
                    // Start dragging the selection
                    isDragging = true
                    isSelecting = false
                    isLassoSelected = false // We hit an element directly, so we don't want the box during drag
                } else {
                    Log.d(TAG, "handleSelectEvent: no element hit, starting lasso selection")
                    // Start Lasso Selection
                    viewModel.deselectAll()
                    isSelecting = true
                    isDragging = false
                    isLassoSelected = false
                    selectionPath = Path().apply { moveTo(x, y) }
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
                    selectionPath?.lineTo(x, y)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "handleSelectEvent: ACTION_UP, isDragging=$isDragging, isSelecting=$isSelecting")
                if (isDragging) {
                    viewModel.commitMove()
                    isLassoSelected = true // Show box after drag is complete
                } else if (isSelecting && selectionPath != null) {
                    selectionPath?.close()
                    val rectF = RectF()
                    selectionPath?.computeBounds(rectF, true)
                    
                    val region = android.graphics.Region()
                    region.setPath(selectionPath!!, android.graphics.Region(
                        rectF.left.toInt(), rectF.top.toInt(), 
                        rectF.right.toInt(), rectF.bottom.toInt()
                    ))

                    val selectedIds = state.elements.filter { element ->
                        val elementBounds = element.getBounds()
                        val elementRegion = android.graphics.Region(
                            elementBounds.left.toInt(), elementBounds.top.toInt(),
                            elementBounds.right.toInt(), elementBounds.bottom.toInt()
                        )
                        !elementRegion.quickReject(region) && elementRegion.op(region, android.graphics.Region.Op.INTERSECT)
                    }.map { it.id }.toSet()

                    Log.d(TAG, "handleSelectEvent: lasso selected ${selectedIds.size} elements")
                    if (selectedIds.isNotEmpty()) {
                        selectedIds.forEach { viewModel.selectElement(it, multiSelect = true) }
                        isLassoSelected = true // Show selection box only after lasso is complete
                    }
                }
                isDragging = false
                isSelecting = false
                selectionPath = null
                return true
            }
        }
        return false
    }
}
