package display.interactive.whiteboard.impl

import android.graphics.Canvas
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import display.interactive.accelerate.AccelerateCanvas
import display.interactive.whiteboard.element.InteractionMode
import display.interactive.whiteboard.interfaces.IWhiteBoardSDK
import display.interactive.whiteboard.state.*

/**
 * Handles touch events and translates them into whiteboard actions.
 * Now refactored to use a parallel state machine for canvas state management.
 */
class TouchHandler(private val viewModel: WhiteBoardSDKImpl) : StateContext {
    
    companion object {
        private const val TAG = "TouchHandler"
    }

    // Implementation of StateContext
    override val sdk: IWhiteBoardSDK get() = viewModel
    override var accelerateCanvas: AccelerateCanvas? = null
    override val scale: Float get() = viewModel.uiState.value.canvasScale
    override val offsetX: Float get() = viewModel.uiState.value.canvasOffsetX
    override val offsetY: Float get() = viewModel.uiState.value.canvasOffsetY

    private val stateMachine = CanvasStateMachine(this)
    private val penState = PenState()
    private val eraserState = EraserState()
    private val zoomPanState = ZoomPanState()
    private val selectedHandler = SelectedHandler(viewModel)
    private val selectionState = SelectionState(selectedHandler)

    private var currentInteractionMode: InteractionMode? = null

    init {
        // Initialize regions
        stateMachine.setRegionState(CanvasStateMachine.Region.NAVIGATION, zoomPanState)
        updateToolState()
    }

    private fun updateToolState() {
        val mode = viewModel.uiState.value.interactionMode
        if (mode == currentInteractionMode) return
        
        currentInteractionMode = mode
        val state = when (mode) {
            InteractionMode.DRAW -> penState
            InteractionMode.SELECT -> selectionState
            InteractionMode.ERASER -> eraserState
            InteractionMode.NAVIGATE -> null // Navigation region handles this if needed
        }
        
        // Reset pen state if we are switching away from it
        if (mode != InteractionMode.DRAW) {
            penState.clearActivePaths()
        }

        if (state != null) {
            stateMachine.setRegionState(CanvasStateMachine.Region.TOOL, state)
        }
    }

    fun handleTouchEvent(event: MotionEvent, scale: Float, offsetX: Float, offsetY: Float): Boolean {
        updateToolState()
        return stateMachine.handleTouchEvent(event)
    }

    fun draw(canvas: Canvas) {
        stateMachine.draw(canvas)
    }

    fun isLassoSelected(): Boolean {
        return selectionState.isLassoSelected()
    }
}
