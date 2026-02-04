package display.interactive.whiteboard.state

import display.interactive.whiteboard.interfaces.IWhiteBoardSDK
import display.interactive.accelerate.AccelerateCanvas

/**
 * Context provided to states to interact with the whiteboard and access shared data.
 */
interface StateContext {
    val sdk: IWhiteBoardSDK
    val accelerateCanvas: AccelerateCanvas?
    
    // Canvas transform info
    val scale: Float
    val offsetX: Float
    val offsetY: Float

    // Screen to World coordinate conversion
    fun toWorldX(screenX: Float): Float = (screenX - offsetX) / scale
    fun toWorldY(screenY: Float): Float = (screenY - offsetY) / scale
}
