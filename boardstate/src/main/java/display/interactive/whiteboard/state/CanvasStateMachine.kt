package display.interactive.whiteboard.state

import android.graphics.Canvas
import android.view.MotionEvent

/**
 * Orchestrates multiple parallel state regions for the whiteboard canvas.
 */
class CanvasStateMachine(private val context: StateContext) {

    /**
     * Defines orthogonal regions that can have independent states.
     */
    enum class Region {
        TOOL,           // Pen, Eraser, Selection
        NAVIGATION,     // Zoom, Pan
        MULTI_TOUCH     // Multi-finger handling (V0.0.9)
    }

    private val regions = mutableMapOf<Region, ICanvasState>()

    /**
     * Sets the active state for a specific region.
     */
    fun setRegionState(region: Region, state: ICanvasState) {
        regions[region]?.onExit(context)
        regions[region] = state
        state.onEnter(context)
    }

    /**
     * Gets the current state for a specific region.
     */
    fun getRegionState(region: Region): ICanvasState? {
        return regions[region]
    }

    /**
     * Delegates touch events to all active parallel states.
     */
    fun handleTouchEvent(event: MotionEvent): Boolean {
        // Priority: Navigation usually takes precedence for multi-touch
        val navState = regions[Region.NAVIGATION]
        if (navState != null && navState.handleTouchEvent(event, -1, context)) {
            // If navigation region consumes the event (e.g. entering Zoom/Pan mode),
            // reset other regions to avoid accidental strokes or selections.
            regions.forEach { (region, state) ->
                if (region != Region.NAVIGATION) {
                    state.reset(context)
                }
            }
            return true
        }

        // Then Tool state
        val toolState = regions[Region.TOOL]
        if (toolState != null) {
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)
            if (toolState.handleTouchEvent(event, pointerId, context)) {
                return true
            }
        }

        // Then Multi-touch logic if any
        val multiState = regions[Region.MULTI_TOUCH]
        if (multiState != null && multiState.handleTouchEvent(event, -1, context)) {
            return true
        }

        return false
    }

    /**
     * Delegates drawing to all active states.
     */
    fun draw(canvas: Canvas) {
        regions.values.forEach { it.draw(canvas, context) }
    }
}
