package display.interactive.whiteboard.state

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import android.view.MotionEvent
import display.interactive.whiteboard.element.BaseElement

/**
 * Handles element selection and lasso selection.
 */
class SelectionState(private val selectedHandler: SelectedHandler) : ICanvasState {
    companion object {
        private const val TAG = "SelectionState"
    }

    private var selectionPath: Path? = null
    private var isSelecting = false
    private var isDragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var isLassoSelected = false

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x402196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    override fun handleTouchEvent(event: MotionEvent, pointerId: Int, context: StateContext): Boolean {
        // Only process the first finger for selection (V0.0.7)
        if (pointerId != 0) return false

        val x = context.toWorldX(event.getX(event.findPointerIndex(pointerId)))
        val y = context.toWorldY(event.getY(event.findPointerIndex(pointerId)))
        val state = context.sdk.uiState.value

        // First, let SelectedHandler handle events if something is already selected
        if (state.selectedElementIds.isNotEmpty() && isLassoSelected) {
            if (selectedHandler.handleTouchEvent(event, state.elements, state.selectedElementIds, x, y)) {
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
                    if (hitElement.id !in state.selectedElementIds) {
                        context.sdk.selectElement(hitElement.id)
                    }
                    isDragging = true
                    isSelecting = false
                    isLassoSelected = false
                } else {
                    context.sdk.deselectAll()
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
                    context.sdk.moveSelectedElements(dx, dy)
                    lastX = x
                    lastY = y
                    return true
                } else if (isSelecting) {
                    selectionPath?.lineTo(x, y)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    context.sdk.commitMove()
                    isLassoSelected = true
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

                    if (selectedIds.isNotEmpty()) {
                        selectedIds.forEach { context.sdk.selectElement(it, multiSelect = true) }
                        isLassoSelected = true
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

    override fun draw(canvas: Canvas, context: StateContext) {
        val state = context.sdk.uiState.value
        
        // Draw selection box and handles via SelectedHandler
        if (isLassoSelected) {
            selectedHandler.draw(canvas, state.elements, state.selectedElementIds, context.scale)
        }

        // Draw lasso selection path
        selectionPath?.let { path ->
            canvas.drawPath(path, selectionPaint)
        }
    }

    override fun reset(context: StateContext) {
        selectionPath = null
        isSelecting = false
        isDragging = false
        isLassoSelected = false
    }

    fun isLassoSelected(): Boolean = isLassoSelected
}
