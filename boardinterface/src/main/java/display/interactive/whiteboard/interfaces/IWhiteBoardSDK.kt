package display.interactive.whiteboard.interfaces

import android.graphics.Bitmap
import android.graphics.RectF
import display.interactive.whiteboard.element.BackgroundType
import display.interactive.whiteboard.element.BaseElement
import display.interactive.whiteboard.element.InteractionMode
import display.interactive.whiteboard.element.StrokeElement
import display.interactive.whiteboard.element.WhiteBoardState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for the WhiteBoard SDK.
 */
interface IWhiteBoardSDK {
    /**
     * The current state of the whiteboard.
     */
    val uiState: StateFlow<WhiteBoardState>

    /**
     * Query elements within a certain range (for frustum culling).
     */
    fun queryElements(range: RectF): Set<BaseElement>

    /**
     * Set the interaction mode (DRAW, SELECT, NAVIGATE, ERASER).
     */
    fun setInteractionMode(mode: InteractionMode)

    /**
     * Set the stroke type (PEN, PENCIL, MARKER).
     */
    fun setStrokeType(type: StrokeElement.StrokeType)

    /**
     * Set the stroke color.
     */
    fun setStrokeColor(color: Int)

    /**
     * Set the stroke width.
     */
    fun setStrokeWidth(width: Float)

    /**
     * Enable or disable multi-finger mode.
     */
    fun setMultiFingerEnabled(enabled: Boolean)

    /**
     * Set zoom/pan mode (V0.0.7).
     */
    fun setZoomMode(enabled: Boolean)

    /**
     * Undo the last operation.
     */
    fun undo()

    /**
     * Redo the last undone operation.
     */
    fun redo()

    /**
     * Clear the entire board.
     */
    fun clear()

    /**
     * Delete all selected elements.
     */
    fun deleteSelectedElements()

    /**
     * Copy selected elements to clipboard.
     */
    fun copySelectedElements()

    /**
     * Duplicate selected elements directly on the board with an offset.
     */
    fun duplicateSelectedElements()

    /**
     * Paste elements from clipboard.
     */
    fun pasteElements()

    /**
     * Bring selected elements to the front.
     */
    fun bringToFront()

    /**
     * Send selected elements to the back.
     */
    fun sendToBack()

    /**
     * Set the background type.
     */
    fun setBackgroundType(type: BackgroundType)

    /**
     * Set the background color.
     */
    fun setBackgroundColor(color: Int)

    /**
     * Set the background image.
     */
    fun setBackgroundImage(bitmap: Bitmap)

    /**
     * Add an element to the board.
     */
    fun addElement(element: BaseElement)

    /**
     * Erase elements at the given world coordinates.
     */
    fun eraseAt(x: Float, y: Float, radius: Float)

    /**
     * Update the eraser position for rendering.
     */
    fun updateEraserPosition(position: android.graphics.PointF?)

    /**
     * Select an element by its ID.
     */
    fun selectElement(id: String, multiSelect: Boolean = false)

    /**
     * Deselect all elements.
     */
    fun deselectAll()

    /**
     * Move selected elements by the given offset in world coordinates.
     */
    fun moveSelectedElements(dx: Float, dy: Float)

    /**
     * Commit the current move operation.
     */
    fun commitMove()

    /**
     * Set the canvas transformation.
     */
    fun setCanvasTransform(scale: Float, offsetX: Float, offsetY: Float)

    /**
     * Scale selected elements.
     */
    fun scaleSelectedElements(sx: Float, sy: Float, pivotX: Float, pivotY: Float)

    /**
     * Rotate selected elements.
     */
    fun rotateSelectedElements(deltaRotation: Float, pivotX: Float, pivotY: Float)

    /**
     * Set color of selected elements.
     */
    fun setSelectedElementsColor(color: Int)
}
