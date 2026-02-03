package display.interactive.whiteboard.interfaces

import android.graphics.Bitmap
import display.interactive.whiteboard.element.BackgroundType
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
     * Add a text element to the whiteboard.
     */
    fun addTextElement(text: String, x: Float, y: Float, fontSize: Float, color: Int)

    /**
     * Add an image element to the whiteboard.
     */
    fun addImageElement(bitmap: Bitmap, x: Float, y: Float, width: Float, height: Float)

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
}
