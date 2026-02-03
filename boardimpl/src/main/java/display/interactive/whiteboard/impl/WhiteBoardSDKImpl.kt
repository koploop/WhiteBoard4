package display.interactive.whiteboard.impl

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import display.interactive.whiteboard.element.*
import display.interactive.whiteboard.interfaces.IWhiteBoardSDK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Stack
import java.util.UUID

/**
 * Implementation of the WhiteBoard SDK.
 */
class WhiteBoardSDKImpl : ViewModel(), IWhiteBoardSDK {

    private val _uiState = MutableStateFlow(WhiteBoardState())
    override val uiState: StateFlow<WhiteBoardState> = _uiState.asStateFlow()

    // Undo/Redo stacks
    private val undoStack = Stack<List<BaseElement>>()
    private val redoStack = Stack<List<BaseElement>>()

    fun addElement(element: BaseElement) {
        saveToUndo()
        _uiState.update { state ->
            state.copy(elements = state.elements + element)
        }
    }

    override fun addTextElement(text: String, x: Float, y: Float, fontSize: Float, color: Int) {
        val element = TextElement(
            id = UUID.randomUUID().toString(),
            text = text,
            fontSize = fontSize,
            color = color,
            x = x,
            y = y,
            zIndex = _uiState.value.elements.size
        )
        addElement(element)
    }

    override fun addImageElement(bitmap: Bitmap, x: Float, y: Float, width: Float, height: Float) {
        val element = ImageElement(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap,
            x = x,
            y = y,
            width = width,
            height = height,
            zIndex = _uiState.value.elements.size
        )
        addElement(element)
    }

    override fun deleteSelectedElements() {
        saveToUndo()
        _uiState.update { state ->
            val newElements = state.elements.filterNot { it.id in state.selectedElementIds }
            state.copy(elements = newElements, selectedElementIds = emptySet())
        }
    }

    override fun clear() {
        saveToUndo()
        _uiState.update { it.copy(elements = emptyList(), selectedElementIds = emptySet()) }
    }

    override fun setInteractionMode(mode: InteractionMode) {
        _uiState.update { it.copy(interactionMode = mode, eraserPosition = null) }
    }

    fun updateEraserPosition(position: PointF?) {
        _uiState.update { it.copy(eraserPosition = position) }
    }

    fun selectElement(id: String, multiSelect: Boolean = false) {
        _uiState.update { state ->
            val newSelection = if (multiSelect) {
                state.selectedElementIds + id
            } else {
                setOf(id)
            }
            state.copy(selectedElementIds = newSelection)
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedElementIds = emptySet()) }
    }

    override fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = _uiState.value.elements
            redoStack.push(currentState)
            val previousState = undoStack.pop()
            _uiState.update { it.copy(elements = previousState) }
        }
    }

    override fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _uiState.value.elements
            undoStack.push(currentState)
            val nextState = redoStack.pop()
            _uiState.update { it.copy(elements = nextState) }
        }
    }

    fun scaleSelectedElements(sx: Float, sy: Float, pivotX: Float, pivotY: Float) {
        _uiState.update { state ->
            state.elements.forEach { element ->
                if (element.id in state.selectedElementIds) {
                    // Scale property
                    element.scaleX *= sx
                    element.scaleY *= sy

                    // Scale position relative to pivot
                    element.x = pivotX + (element.x - pivotX) * sx
                    element.y = pivotY + (element.y - pivotY) * sy
                }
            }
            state.copy(elements = state.elements)
        }
    }

    fun rotateSelectedElements(deltaRotation: Float, pivotX: Float, pivotY: Float) {
        _uiState.update { state ->
            state.elements.forEach { element ->
                if (element.id in state.selectedElementIds) {
                    // Rotate property
                    element.rotation += deltaRotation

                    // Rotate position relative to pivot
                    val dx = element.x - pivotX
                    val dy = element.y - pivotY
                    val cos = Math.cos(Math.toRadians(deltaRotation.toDouble())).toFloat()
                    val sin = Math.sin(Math.toRadians(deltaRotation.toDouble())).toFloat()

                    element.x = pivotX + (dx * cos - dy * sin)
                    element.y = pivotY + (dx * sin + dy * cos)
                }
            }
            state.copy(elements = state.elements)
        }
    }

    fun setSelectedElementsColor(color: Int) {
        saveToUndo()
        _uiState.update { state ->
            state.elements.forEach { element ->
                if (element.id in state.selectedElementIds) {
                    when (element) {
                        is StrokeElement -> {
                            element.color = color
                        }
                        is TextElement -> {
                            element.color = color
                        }
                    }
                }
            }
            state.copy(elements = state.elements)
        }
    }

    private fun saveToUndo() {
        undoStack.push(_uiState.value.elements)
        redoStack.clear()
    }

    override fun setBackgroundType(type: BackgroundType) {
        _uiState.update { it.copy(backgroundState = it.backgroundState.copy(type = type)) }
    }

    override fun setBackgroundColor(color: Int) {
        _uiState.update { it.copy(backgroundState = it.backgroundState.copy(color = color)) }
    }

    override fun setBackgroundImage(bitmap: Bitmap) {
        _uiState.update { it.copy(backgroundState = it.backgroundState.copy(type = BackgroundType.IMAGE, image = bitmap)) }
    }

    fun updateElement(element: BaseElement) {
        _uiState.update { state ->
            val newElements = state.elements.map { if (it.id == element.id) element else it }
            state.copy(elements = newElements)
        }
    }

    fun moveSelectedElements(dx: Float, dy: Float) {
        _uiState.update { state ->
            state.elements.forEach { element ->
                if (element.id in state.selectedElementIds) {
                    element.x += dx
                    element.y += dy
                }
            }
            state.copy(elements = state.elements)
        }
    }

    fun eraseAt(x: Float, y: Float, radius: Float) {
        _uiState.update { state ->
            val newElements = mutableListOf<BaseElement>()
            var changed = false
            state.elements.forEach { element ->
                val result = element.erase(x, y, radius)
                if (result.size != 1 || result[0] !== element) {
                    changed = true
                    newElements.addAll(result)
                } else {
                    newElements.add(element)
                }
            }
            if (changed) {
                state.copy(elements = newElements)
            } else {
                state
            }
        }
    }

    fun commitMove() {
        saveToUndo()
    }

    override fun bringToFront() {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return
        saveToUndo()
        _uiState.update { state ->
            val elements = state.elements.toMutableList()
            val selectedElements = elements.filter { it.id in selectedIds }
            elements.removeAll(selectedElements)
            elements.addAll(selectedElements)
            // Update z-indices
            elements.forEachIndexed { index, element -> element.zIndex = index }
            state.copy(elements = elements)
        }
    }

    override fun sendToBack() {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return
        saveToUndo()
        _uiState.update { state ->
            val elements = state.elements.toMutableList()
            val selectedElements = elements.filter { it.id in selectedIds }
            elements.removeAll(selectedElements)
            elements.addAll(0, selectedElements)
            // Update z-indices
            elements.forEachIndexed { index, element -> element.zIndex = index }
            state.copy(elements = elements)
        }
    }

    private var clipboard: List<BaseElement>? = null

    override fun copySelectedElements() {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return
        clipboard = _uiState.value.elements.filter { it.id in selectedIds }
    }

    override fun pasteElements() {
        val toPaste = clipboard ?: return
        saveToUndo()
        _uiState.update { state ->
            val newElements = toPaste.map { element ->
                // Create a copy with a new ID and slightly offset position
                when (element) {
                    is StrokeElement -> StrokeElement(
                        id = UUID.randomUUID().toString(),
                        points = element.points.map { p -> PointF(p.x + 20f, p.y + 20f) },
                        color = element.color,
                        strokeWidth = element.strokeWidth,
                        type = element.type,
                        zIndex = state.elements.size
                    )
                    is TextElement -> TextElement(
                        id = UUID.randomUUID().toString(),
                        text = element.text,
                        fontSize = element.fontSize,
                        color = element.color,
                        x = element.x + 20f,
                        y = element.y + 20f,
                        zIndex = state.elements.size
                    )
                    is ImageElement -> ImageElement(
                        id = UUID.randomUUID().toString(),
                        bitmap = element.bitmap,
                        x = element.x + 20f,
                        y = element.y + 20f,
                        width = element.bitmap.width * element.scaleX,
                        height = element.bitmap.height * element.scaleY,
                        zIndex = state.elements.size
                    )
                    else -> element // Should not happen
                }
            }
            state.copy(elements = state.elements + newElements)
        }
    }

    override fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(currentStrokeWidth = width) }
    }

    override fun setStrokeColor(color: Int) {
        _uiState.update { it.copy(currentStrokeColor = color) }
    }

    override fun setStrokeType(type: StrokeElement.StrokeType) {
        _uiState.update { it.copy(currentStrokeType = type) }
    }

    override fun setMultiFingerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isMultiFingerEnabled = enabled) }
    }

    override fun setZoomMode(enabled: Boolean) {
        _uiState.update { it.copy(isZoomMode = enabled) }
    }

    fun setCanvasTransform(scale: Float, offsetX: Float, offsetY: Float) {
        _uiState.update { it.copy(canvasScale = scale, canvasOffsetX = offsetX, canvasOffsetY = offsetY) }
    }
}
