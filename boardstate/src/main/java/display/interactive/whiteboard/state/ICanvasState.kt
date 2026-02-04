package display.interactive.whiteboard.state

import android.graphics.Canvas
import android.view.MotionEvent

/**
 * Interface for canvas states in the parallel state machine.
 */
interface ICanvasState {
    /**
     * Handles touch events for this state.
     * @return true if the event was consumed.
     */
    fun handleTouchEvent(event: MotionEvent, pointerId: Int, context: StateContext): Boolean

    /**
     * Called when the state is entered.
     */
    fun onEnter(context: StateContext) {}

    /**
     * Called when the state is exited.
     */
    fun onExit(context: StateContext) {}

    /**
     * Optional drawing logic for the state (e.g., drawing selection box).
     */
    fun draw(canvas: Canvas, context: StateContext) {}

    /**
     * Resets the state, cancelling any ongoing operations.
     */
    fun reset(context: StateContext) {}
}
