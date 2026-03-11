package display.interactive.whiteboard.impl

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import display.interactive.whiteboard.element.*
import display.interactive.whiteboard.interfaces.IWhiteBoardSDK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Stack

/**
 * Implementation of the WhiteBoard SDK.
 */
class WhiteBoardSDKImpl : ViewModel(), IWhiteBoardSDK {

    private val _uiState = MutableStateFlow(WhiteBoardState())
    override val uiState: StateFlow<WhiteBoardState> = _uiState.asStateFlow()

    // QuadTree for spatial indexing
    private var quadTree: QuadTree = QuadTree(RectF(-100000f, -100000f, 100000f, 100000f))

    override fun queryElements(range: RectF): Set<BaseElement> {
        val result = mutableSetOf<BaseElement>()
        quadTree.query(range, result)
        return result
    }

    // Undo/Redo stacks
    private val undoStack = Stack<List<BaseElement>>()
    private val redoStack = Stack<List<BaseElement>>()

    private fun updateElementsState(
        newElements: List<BaseElement>, 
        selectedIds: Set<String>? = null,
        incrementStructural: Boolean = true
    ) {
        val sorted = newElements.sortedBy { it.zIndex }
        
        // Update QuadTree
        quadTree.clear()
        sorted.forEach { quadTree.insert(it) }

        _uiState.update { state ->
            val resolvedSelectedIds = selectedIds ?: state.selectedElementIds
            val activeNoteId = resolveActiveNoteId(resolvedSelectedIds, newElements)
            val noteMode = if (activeNoteId == null) NoteInteractionMode.NONE else state.noteInteractionMode
            state.copy(
                elements = newElements,
                sortedElements = sorted,
                selectedElementIds = resolvedSelectedIds,
                structuralVersion = if (incrementStructural) state.structuralVersion + 1 else state.structuralVersion,
                activeNoteId = activeNoteId,
                noteInteractionMode = noteMode,
                uiVersion = state.uiVersion + 1
            )
        }
    }

    override fun addElement(element: BaseElement) {
        saveToUndo()
        updateElementsState(_uiState.value.elements + element)
    }

    override fun deleteSelectedElements() {
        saveToUndo()
        val newElements = _uiState.value.elements.filterNot { it.id in _uiState.value.selectedElementIds }
        updateElementsState(newElements, emptySet())
    }

    override fun clear() {
        saveToUndo()
        updateElementsState(emptyList(), emptySet())
    }

    override fun setInteractionMode(mode: InteractionMode) {
        _uiState.update {
            it.copy(
                interactionMode = mode,
                eraserPosition = null,
                noteInteractionMode = if (mode == InteractionMode.DRAW) it.noteInteractionMode else NoteInteractionMode.NONE
            )
        }
    }

    override fun updateEraserPosition(position: PointF?) {
        _uiState.update { it.copy(eraserPosition = position) }
    }

    override fun selectElement(id: String, multiSelect: Boolean) {
        _uiState.update { state ->
            val newSelection = if (multiSelect) {
                state.selectedElementIds + id
            } else {
                setOf(id)
            }
            val activeNoteId = resolveActiveNoteId(newSelection, state.elements)
            state.copy(
                selectedElementIds = newSelection,
                structuralVersion = state.structuralVersion + 1,
                activeNoteId = activeNoteId,
                noteInteractionMode = if (activeNoteId == null) NoteInteractionMode.NONE else state.noteInteractionMode
            )
        }
    }

    override fun deselectAll() {
        _uiState.update { state ->
            state.copy(
                selectedElementIds = emptySet(),
                structuralVersion = state.structuralVersion + 1,
                activeNoteId = null,
                noteInteractionMode = NoteInteractionMode.NONE
            )
        }
    }

    override fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = _uiState.value.elements
            redoStack.push(currentState)
            val previousState = undoStack.pop()
            updateElementsState(previousState)
        }
    }

    override fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _uiState.value.elements
            undoStack.push(currentState)
            val nextState = redoStack.pop()
            updateElementsState(nextState)
        }
    }

    override fun scaleSelectedElements(sx: Float, sy: Float, pivotX: Float, pivotY: Float) {
        val elements = _uiState.value.elements
        elements.forEach { element ->
            if (element.id in _uiState.value.selectedElementIds) {
                element.scaleX *= sx
                element.scaleY *= sy

                element.x = pivotX + (element.x - pivotX) * sx
                element.y = pivotY + (element.y - pivotY) * sy
                notifyTransformListener(element, scaleX = sx, scaleY = sy)
            }
        }
        updateElementsState(elements, incrementStructural = false)
    }

    override fun rotateSelectedElements(deltaRotation: Float, pivotX: Float, pivotY: Float) {
        val elements = _uiState.value.elements
        elements.forEach { element ->
            if (element.id in _uiState.value.selectedElementIds) {
                element.rotation += deltaRotation

                val dx = element.x - pivotX
                val dy = element.y - pivotY
                val cos = Math.cos(Math.toRadians(deltaRotation.toDouble())).toFloat()
                val sin = Math.sin(Math.toRadians(deltaRotation.toDouble())).toFloat()

                element.x = pivotX + (dx * cos - dy * sin)
                element.y = pivotY + (dx * sin + dy * cos)
                notifyTransformListener(element, rotation = deltaRotation)
            }
        }
        updateElementsState(elements, incrementStructural = false)
    }

    override fun setSelectedElementsColor(color: Int) {
        saveToUndo()
        val elements = _uiState.value.elements
        elements.forEach { element ->
            if (element.id in _uiState.value.selectedElementIds) {
                when (element) {
                    is StrokeElement -> {
                        element.color = color
                    }
                    is NoteElement -> {
                        element.backgroundColor = color
                    }
                }
            }
        }
        updateElementsState(elements)
    }

    private fun saveToUndo() {
        undoStack.push(_uiState.value.elements)
        redoStack.clear()
    }

    private fun resolveActiveNoteId(selectedIds: Set<String>, elements: List<BaseElement>): String? {
        if (selectedIds.size != 1) return null
        val selectedId = selectedIds.first()
        return if (elements.any { it.id == selectedId && it is NoteElement }) selectedId else null
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
        val newElements = _uiState.value.elements.map { if (it.id == element.id) element else it }
        updateElementsState(newElements)
    }

    override fun moveSelectedElements(dx: Float, dy: Float) {
        val elements = _uiState.value.elements
        elements.forEach { element ->
            if (element.id in _uiState.value.selectedElementIds) {
                element.x += dx
                element.y += dy
                notifyTransformListener(element, dx = dx, dy = dy)
            }
        }
        updateElementsState(elements, incrementStructural = false)
    }

    override fun eraseAt(x: Float, y: Float, radius: Float) {
        // Use QuadTree to find candidate elements
        val range = RectF(x - radius, y - radius, x + radius, y + radius)
        val candidates = mutableSetOf<BaseElement>()
        quadTree.query(range, candidates)

        if (candidates.isEmpty()) return

        val elements = _uiState.value.elements
        val newElements = mutableListOf<BaseElement>()
        var changed = false

        elements.forEach { element ->
            if (element in candidates) {
                val result = element.erase(x, y, radius)
                if (result.size != 1 || result[0] !== element) {
                    changed = true
                    newElements.addAll(result)
                } else {
                    newElements.add(element)
                }
            } else {
                newElements.add(element)
            }
        }

        if (changed) {
            updateElementsState(newElements)
        }
    }

    override fun commitMove() {
        saveToUndo()
    }

    override fun bringToFront() {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return
        saveToUndo()
        val elements = _uiState.value.elements.toMutableList()
        val selectedElements = elements.filter { it.id in selectedIds }
        elements.removeAll(selectedElements)
        elements.addAll(selectedElements)
        // Update z-indices
        elements.forEachIndexed { index, element -> element.zIndex = index }
        updateElementsState(elements)
    }

    override fun sendToBack() {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return
        saveToUndo()
        val elements = _uiState.value.elements.toMutableList()
        val selectedElements = elements.filter { it.id in selectedIds }
        elements.removeAll(selectedElements)
        elements.addAll(0, selectedElements)
        // Update z-indices
        elements.forEachIndexed { index, element -> element.zIndex = index }
        updateElementsState(elements)
    }

    private var clipboard: List<BaseElement>? = null

    override fun copySelectedElements() {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return
        clipboard = _uiState.value.elements.filter { it.id in selectedIds }
    }

    override fun duplicateSelectedElements() {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return
        
        saveToUndo()
        val elements = _uiState.value.elements
        val selectedElements = elements.filter { it.id in selectedIds }
        val newElements = selectedElements.map { element ->
            val copy = element.copy()
            copy.x += 50f
            copy.y += 50f
            copy.zIndex = elements.size + selectedElements.indexOf(element)
            copy
        }
        val newSelectedIds = newElements.map { it.id }.toSet()
        updateElementsState(elements + newElements, newSelectedIds)
    }

    override fun pasteElements() {
        val toPaste = clipboard ?: return
        saveToUndo()
        val elements = _uiState.value.elements
        val newElements = toPaste.mapIndexed { index, element ->
            val copy = element.copy()
            copy.x += 20f
            copy.y += 20f
            copy.zIndex = elements.size + index
            copy
        }
        updateElementsState(elements + newElements)
    }

    private fun notifyTransformListener(
        element: BaseElement,
        dx: Float? = null,
        dy: Float? = null,
        scaleX: Float? = null,
        scaleY: Float? = null,
        rotation: Float? = null
    ) {
        val listener = element.transformListener ?: return
        if (dx != null && dy != null) {
            listener.onMove(dx, dy)
        }
        if (scaleX != null && scaleY != null) {
            listener.onScale(scaleX, scaleY)
        }
        if (rotation != null) {
            listener.onRotate(rotation)
        }
        listener.onMatrixChanged(element.getTransformMatrix())
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

    override fun setFingerSeparateMode(enabled: Boolean) {
        _uiState.update { it.copy(isFingerSeparateMode = enabled) }
    }

    override fun setCanvasTransform(scale: Float, offsetX: Float, offsetY: Float) {
        _uiState.update { it.copy(canvasScale = scale, canvasOffsetX = offsetX, canvasOffsetY = offsetY) }
    }

    override fun setNoteInteractionMode(mode: NoteInteractionMode) {
        _uiState.update { state ->
            if (state.activeNoteId == null) {
                state.copy(noteInteractionMode = NoteInteractionMode.NONE)
            } else {
                state.copy(noteInteractionMode = mode)
            }
        }
    }

    override fun clearNoteInteractionMode() {
        _uiState.update { it.copy(noteInteractionMode = NoteInteractionMode.NONE) }
    }

    override fun updateActiveNoteText(text: String) {
        val state = _uiState.value
        val noteId = state.activeNoteId ?: return
        val elements = state.elements
        val note = elements.find { it.id == noteId } as? NoteElement ?: return
        note.updateText(text)
        updateElementsState(elements, incrementStructural = false)
    }

    override fun beginNoteAnnotation(pointerId: Int, x: Float, y: Float) {
        val state = _uiState.value
        val noteId = state.activeNoteId ?: return
        val note = state.elements.find { it.id == noteId } as? NoteElement ?: return
        note.beginAnnotation(pointerId, x, y, state.currentStrokeColor, state.currentStrokeWidth)
        updateElementsState(state.elements, incrementStructural = false)
    }

    override fun appendNoteAnnotation(pointerId: Int, x: Float, y: Float) {
        val state = _uiState.value
        val noteId = state.activeNoteId ?: return
        val note = state.elements.find { it.id == noteId } as? NoteElement ?: return
        note.appendAnnotation(pointerId, x, y)
        updateElementsState(state.elements, incrementStructural = false)
    }

    override fun endNoteAnnotation(pointerId: Int) {
        val state = _uiState.value
        val noteId = state.activeNoteId ?: return
        val note = state.elements.find { it.id == noteId } as? NoteElement ?: return
        note.endAnnotation(pointerId)
        updateElementsState(state.elements)
    }

    override fun eraseNoteAnnotationAt(x: Float, y: Float, radius: Float) {
        val state = _uiState.value
        val noteId = state.activeNoteId ?: return
        val note = state.elements.find { it.id == noteId } as? NoteElement ?: return
        note.erase(x, y, radius)
        updateElementsState(state.elements)
    }
}
