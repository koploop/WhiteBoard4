package display.interactive.whiteboard.impl

import android.graphics.Canvas
import android.view.MotionEvent
import display.interactive.accelerate.AccelerateCanvas
import display.interactive.whiteboard.element.InteractionMode
import display.interactive.whiteboard.element.NativeViewElement
import display.interactive.whiteboard.element.NoteInteractionMode
import display.interactive.whiteboard.interfaces.IWhiteBoardSDK
import display.interactive.whiteboard.state.*

/**
 * Handles touch events and translates them into whiteboard actions.
 * Now refactored to use a parallel state machine for canvas state management.
 */
class TouchHandler(private val viewModel: WhiteBoardSDKImpl) : StateContext {

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

    /**
     * 处理触摸事件，将其转换为白板操作。
     * 优先处理 NativeViewElement 的点击与文本编辑状态的退出，
     * 再交由状态机分发后续手势。
     *
     * @param event   原始触摸事件
     * @param scale   当前画布缩放比例
     * @param offsetX 当前画布水平偏移
     * @param offsetY 当前画布垂直偏移
     * @return true 表示事件已消费；false 表示未消费
     */
    fun handleTouchEvent(event: MotionEvent, scale: Float, offsetX: Float, offsetY: Float): Boolean {
        // 仅在手指初次按下时做“命中检测”与“模式切换”处理
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val state = viewModel.uiState.value

            // 将屏幕坐标转换为白板世界坐标
            val worldX = (event.x - offsetX) / scale
            val worldY = (event.y - offsetY) / scale

            // 逆序遍历元素，找到最顶层的 NativeViewElement
            val hitNativeViewElement = state.elements.asReversed().firstOrNull {
                it is NativeViewElement && it.contains(worldX, worldY)
            }

            if (hitNativeViewElement != null) {
                // 判断当前是否已单选该元素且处于可继续手势状态
                val isAlreadySingleSelected = state.selectedElementIds.size == 1 &&
                        hitNativeViewElement.id in state.selectedElementIds
                val canContinueSelectionGesture = state.interactionMode == InteractionMode.SELECT &&
                        isAlreadySingleSelected &&
                        state.noteInteractionMode == NoteInteractionMode.NONE

                // 若不能继续已有手势，则重新选中该元素并切换到选择模式
                if (!canContinueSelectionGesture) {
                    viewModel.selectElement(hitNativeViewElement.id)   // 选中元素
                    viewModel.clearNoteInteractionMode()               // 清除笔记交互模式
                    // viewModel.setInteractionMode(InteractionMode.SELECT) // 切换为选择工具
                    // updateToolState()                                    // 同步状态机
                    return true                                          // 事件已处理
                }
            }

            // 如果当前处于文本编辑模式，点击空白处则退出编辑
            if (state.noteInteractionMode == NoteInteractionMode.TEXT_EDIT) {
                viewModel.clearNoteInteractionMode()
                return true
            }
        }

        // 同步当前工具状态（模式可能在上层被改变）
        updateToolState()
        // 将事件交给状态机继续处理（绘制、擦除、缩放平移等）
        return stateMachine.handleTouchEvent(event)
    }

    fun draw(canvas: Canvas) {
        stateMachine.draw(canvas)
    }

    fun isLassoSelected(): Boolean {
        return selectionState.isLassoSelected()
    }
}
