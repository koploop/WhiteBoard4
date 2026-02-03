package display.interactive.whiteboard.impl

import android.graphics.*
import android.view.MotionEvent
import display.interactive.whiteboard.element.BaseElement
import display.interactive.whiteboard.element.SelectedAbility
import display.interactive.whiteboard.element.StrokeElement
import display.interactive.whiteboard.element.TextElement
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Handles selection box drawing and interactions (move, scale, rotate, menu).
 */
class SelectedHandler(private val sdk: WhiteBoardSDKImpl) {

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val menuBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 5f, 0x40000000)
    }

    private val handleSize = 30f
    private val rotateHandleOffset = 60f
    private val menuOffset = 40f
    private val menuItemSize = 60f

    private var groupRotation = 0f
    private var baseBounds = RectF()
    private var lastSelectedIds = emptySet<String>()

    private var currentMode = OperationMode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var initialRotation = 0f
    private var initialPivotX = 0f
    private var initialPivotY = 0f

    private enum class OperationMode {
        NONE, MOVE, SCALE_TOP_LEFT, SCALE_TOP_RIGHT, SCALE_BOTTOM_LEFT, SCALE_BOTTOM_RIGHT, ROTATE, MENU
    }

    private val menuItems = mutableListOf<MenuItem>()

    private data class MenuItem(val ability: SelectedAbility, val rect: RectF)

    fun draw(canvas: Canvas, elements: List<BaseElement>, selectedIds: Set<String>, scale: Float) {
        if (selectedIds.isEmpty()) return

        // Always update selection state from current element positions
        val selectedElements = elements.filter { it.id in selectedIds }
        if (selectedElements.isEmpty()) return

        if (selectedIds != lastSelectedIds) {
            lastSelectedIds = selectedIds
            if (selectedIds.size == 1) {
                val element = selectedElements[0]
                groupRotation = element.rotation
                baseBounds.set(element.getBounds())
            } else {
                groupRotation = 0f
                baseBounds.set(getCombinedRotatedBounds(selectedElements))
                // Inset slightly to make the box look better
                baseBounds.inset(-10f, -10f)
            }
        } else {
            // Update bounds for existing selection (to follow moving elements)
            if (selectedIds.size == 1) {
                val element = selectedElements[0]
                groupRotation = element.rotation
                baseBounds.set(element.getBounds())
            } else {
                // For multi-selection, we need to be careful not to reset rotation if we are currently rotating
                if (currentMode != OperationMode.ROTATE) {
                    baseBounds.set(getCombinedRotatedBounds(selectedElements))
                    baseBounds.inset(-10f, -10f)
                }
            }
        }

        if (baseBounds.isEmpty) return

        val canScale = selectedElements.all { it.hasSelectedAbility(SelectedAbility.SCALE) }
        val canRotate = selectedElements.all { it.hasSelectedAbility(SelectedAbility.ROTATE) }

        // Draw selection box and handles
        canvas.save()
        val centerX = baseBounds.centerX()
        val centerY = baseBounds.centerY()
        canvas.rotate(groupRotation, centerX, centerY)

        // Draw selection rectangle
        canvas.drawRect(baseBounds, selectionPaint)

        if (canScale) {
            drawHandle(canvas, baseBounds.left, baseBounds.top)
            drawHandle(canvas, baseBounds.right, baseBounds.top)
            drawHandle(canvas, baseBounds.left, baseBounds.bottom)
            drawHandle(canvas, baseBounds.right, baseBounds.bottom)
        }

        if (canRotate) {
            val rotateX = baseBounds.centerX()
            val rotateY = baseBounds.top - rotateHandleOffset
            canvas.drawLine(baseBounds.centerX(), baseBounds.top, rotateX, rotateY, selectionPaint)
            drawHandle(canvas, rotateX, rotateY, isCircle = true)
        }

        canvas.restore()

        // Draw menu upright at the bottom of the VISUAL AABB of the rotated box
        // This visual AABB changes as we rotate, which matches "变的只是视觉上的外接矩形"
        val visualBounds = getVisualAABB(baseBounds, groupRotation)
        drawMenu(canvas, visualBounds, selectedElements)
    }

    private fun getVisualAABB(rect: RectF, rotation: Float): RectF {
        if (rotation == 0f) return rect
        val matrix = Matrix()
        matrix.postRotate(rotation, rect.centerX(), rect.centerY())
        val rotatedRect = RectF()
        matrix.mapRect(rotatedRect, rect)
        return rotatedRect
    }

    private fun getCombinedRotatedBounds(elements: List<BaseElement>): RectF {
        val combined = RectF()
        var first = true
        elements.forEach { element ->
            val rBounds = element.getRotatedBounds()
            if (first) {
                combined.set(rBounds)
                first = false
            } else {
                combined.union(rBounds)
            }
        }
        return combined
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float, isCircle: Boolean = false) {
        if (isCircle) {
            canvas.drawCircle(x, y, handleSize / 2, handlePaint)
            canvas.drawCircle(x, y, handleSize / 2, handleStrokePaint)
        } else {
            canvas.drawRect(x - handleSize / 2, y - handleSize / 2, x + handleSize / 2, y + handleSize / 2, handlePaint)
            canvas.drawRect(x - handleSize / 2, y - handleSize / 2, x + handleSize / 2, y + handleSize / 2, handleStrokePaint)
        }
    }

    private fun drawMenu(canvas: Canvas, bounds: RectF, selectedElements: List<BaseElement>) {
        val menuY = bounds.bottom + menuOffset
        val abilities = mutableSetOf<SelectedAbility>()
        selectedElements.forEach { element ->
            if (element.hasSelectedAbility(SelectedAbility.CHANGE_COLOR)) abilities.add(SelectedAbility.CHANGE_COLOR)
            if (element.hasSelectedAbility(SelectedAbility.DELETE)) abilities.add(SelectedAbility.DELETE)
            if (element.hasSelectedAbility(SelectedAbility.COPY)) abilities.add(SelectedAbility.COPY)
            if (element.hasSelectedAbility(SelectedAbility.CHANGE_ORDER)) abilities.add(SelectedAbility.CHANGE_ORDER)
        }

        if (abilities.isEmpty()) return

        val sortedAbilities = abilities.toList().sortedBy { it.ordinal }
        val menuWidth = sortedAbilities.size * menuItemSize
        val menuRect = RectF(bounds.centerX() - menuWidth / 2, menuY, bounds.centerX() + menuWidth / 2, menuY + menuItemSize)

        canvas.drawRoundRect(menuRect, 10f, 10f, menuBackgroundPaint)

        menuItems.clear()
        sortedAbilities.forEachIndexed { index, ability ->
            val itemRect = RectF(
                menuRect.left + index * menuItemSize,
                menuRect.top,
                menuRect.left + (index + 1) * menuItemSize,
                menuRect.bottom
            )
            menuItems.add(MenuItem(ability, itemRect))

            // Draw placeholder for icon
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
                textAlign = Paint.Align.CENTER
            }
            val label = when(ability) {
                SelectedAbility.CHANGE_COLOR -> "Color"
                SelectedAbility.DELETE -> "Del"
                SelectedAbility.COPY -> "Copy"
                SelectedAbility.CHANGE_ORDER -> "Layer"
                else -> ""
            }
            canvas.drawText(label, itemRect.centerX(), itemRect.centerY() + 10f, textPaint)
        }
    }

    fun handleTouchEvent(event: MotionEvent, elements: List<BaseElement>, selectedIds: Set<String>, x: Float, y: Float): Boolean {
        if (selectedIds.isEmpty()) return false
        if (baseBounds.isEmpty) return false

        val selectedElements = elements.filter { it.id in selectedIds }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                currentMode = getModeAt(x, y, baseBounds, selectedElements)
                if (currentMode == OperationMode.ROTATE) {
                    initialPivotX = baseBounds.centerX()
                    initialPivotY = baseBounds.centerY()
                    initialRotation = atan2(y - initialPivotY, x - initialPivotX)
                } else if (currentMode == OperationMode.MENU) {
                    val clickedItem = menuItems.find { it.rect.contains(x, y) }
                    clickedItem?.let { handleMenuAction(it.ability) }
                    return true
                }
                return currentMode != OperationMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentMode == OperationMode.NONE || currentMode == OperationMode.MENU) return false

                val dx = x - lastX
                val dy = y - lastY

                when (currentMode) {
                    OperationMode.MOVE -> {
                        baseBounds.offset(dx, dy)
                        sdk.moveSelectedElements(dx, dy)
                        selectedElements.forEach { it.transformListener?.onMove(dx, dy) }
                    }
                    OperationMode.SCALE_TOP_LEFT, OperationMode.SCALE_TOP_RIGHT,
                    OperationMode.SCALE_BOTTOM_LEFT, OperationMode.SCALE_BOTTOM_RIGHT -> {
                        handleScale(dx, dy, baseBounds, selectedElements)
                    }
                    OperationMode.ROTATE -> {
                        val currentRot = atan2(y - initialPivotY, x - initialPivotX)
                        val deltaRot = Math.toDegrees((currentRot - initialRotation).toDouble()).toFloat()
                        handleRotate(deltaRot, initialPivotX, initialPivotY, selectedElements)
                        initialRotation = currentRot // Update for next move
                    }
                    else -> {}
                }

                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (currentMode != OperationMode.NONE) {
                    sdk.commitMove()
                    currentMode = OperationMode.NONE
                    return true
                }
            }
        }
        return false
    }

    private fun handleMenuAction(ability: SelectedAbility) {
        when (ability) {
            SelectedAbility.DELETE -> sdk.deleteSelectedElements()
            SelectedAbility.COPY -> sdk.copySelectedElements()
            SelectedAbility.CHANGE_ORDER -> {
                // Toggle between front/back for simplicity, or we could show another menu
                sdk.bringToFront()
            }
            SelectedAbility.CHANGE_COLOR -> {
                // Show color picker - for now just cycle colors
                val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.YELLOW)
                val currentColor = (sdk.uiState.value.elements.find { it.id in sdk.uiState.value.selectedElementIds } as? StrokeElement)?.color ?: Color.BLACK
                val nextColor = colors[(colors.indexOf(currentColor) + 1) % colors.size]
                sdk.setSelectedElementsColor(nextColor)
            }
            else -> {}
        }
    }

    private fun getModeAt(x: Float, y: Float, bounds: RectF, selectedElements: List<BaseElement>): OperationMode {
        // Check menu first (menu is NOT rotated)
        if (menuItems.any { it.rect.contains(x, y) }) return OperationMode.MENU

        // Transform touch point to local unrotated space of the selection box
        val matrix = Matrix()
        matrix.postRotate(-groupRotation, bounds.centerX(), bounds.centerY())
        val pts = floatArrayOf(x, y)
        matrix.mapPoints(pts)
        val localX = pts[0]
        val localY = pts[1]

        val canScale = selectedElements.all { it.hasSelectedAbility(SelectedAbility.SCALE) }
        val canRotate = selectedElements.all { it.hasSelectedAbility(SelectedAbility.ROTATE) }

        if (canScale) {
            if (isHit(localX, localY, bounds.left, bounds.top)) return OperationMode.SCALE_TOP_LEFT
            if (isHit(localX, localY, bounds.right, bounds.top)) return OperationMode.SCALE_TOP_RIGHT
            if (isHit(localX, localY, bounds.left, bounds.bottom)) return OperationMode.SCALE_BOTTOM_LEFT
            if (isHit(localX, localY, bounds.right, bounds.bottom)) return OperationMode.SCALE_BOTTOM_RIGHT
        }

        if (canRotate) {
            if (isHit(localX, localY, bounds.centerX(), bounds.top - rotateHandleOffset)) return OperationMode.ROTATE
        }

        if (bounds.contains(localX, localY)) return OperationMode.MOVE
        return OperationMode.NONE
    }

    private fun isHit(tx: Float, ty: Float, hx: Float, hy: Float): Boolean {
        val dist = sqrt(((tx - hx) * (tx - hx) + (ty - hy) * (ty - hy)).toDouble())
        return dist < handleSize * 1.5f // Larger hit area
    }

    private fun handleScale(dx: Float, dy: Float, bounds: RectF, elements: List<BaseElement>) {
        val oldWidth = bounds.width()
        val oldHeight = bounds.height()
        if (oldWidth <= 10f || oldHeight <= 10f) return

        // Transform movement to local coordinate system
        val matrix = Matrix()
        matrix.postRotate(-groupRotation)
        val dpts = floatArrayOf(dx, dy)
        matrix.mapPoints(dpts)
        val localDx = dpts[0]
        val localDy = dpts[1]

        var sx = 1f
        var sy = 1f
        var pivotX = bounds.centerX()
        var pivotY = bounds.centerY()

        when (currentMode) {
            OperationMode.SCALE_TOP_LEFT -> {
                sx = (oldWidth - localDx) / oldWidth
                sy = (oldHeight - localDy) / oldHeight
                pivotX = bounds.right
                pivotY = bounds.bottom
            }
            OperationMode.SCALE_TOP_RIGHT -> {
                sx = (oldWidth + localDx) / oldWidth
                sy = (oldHeight - localDy) / oldHeight
                pivotX = bounds.left
                pivotY = bounds.bottom
            }
            OperationMode.SCALE_BOTTOM_LEFT -> {
                sx = (oldWidth - localDx) / oldWidth
                sy = (oldHeight + localDy) / oldHeight
                pivotX = bounds.right
                pivotY = bounds.top
            }
            OperationMode.SCALE_BOTTOM_RIGHT -> {
                sx = (oldWidth + localDx) / oldWidth
                sy = (oldHeight + localDy) / oldHeight
                pivotX = bounds.left
                pivotY = bounds.top
            }
            else -> return
        }

        // Apply proportional constraint: use the one with larger change
        val s = if (Math.abs(sx - 1f) > Math.abs(sy - 1f)) sx else sy
        if (s <= 0.01f) return

        // Pivot also needs to be rotated back to world space for the SDK call
        val pMatrix = Matrix()
        pMatrix.postRotate(groupRotation, bounds.centerX(), bounds.centerY())
        val ppts = floatArrayOf(pivotX, pivotY)
        pMatrix.mapPoints(ppts)
        val worldPivotX = ppts[0]
        val worldPivotY = ppts[1]

        sdk.scaleSelectedElements(s, s, worldPivotX, worldPivotY)

        // Update baseBounds
        val scaleMatrix = Matrix()
        scaleMatrix.postScale(s, s, pivotX, pivotY)
        scaleMatrix.mapRect(baseBounds)

        elements.forEach { it.transformListener?.onScale(s, s) }
    }

    private fun handleRotate(deltaRot: Float, pivotX: Float, pivotY: Float, elements: List<BaseElement>) {
        groupRotation += deltaRot
        sdk.rotateSelectedElements(deltaRot, pivotX, pivotY)
        elements.forEach { it.transformListener?.onRotate(deltaRot) }
    }
}
