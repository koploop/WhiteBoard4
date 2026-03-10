# 需求设计分析
记录对各个版本需求的理解思考,设计思路和行动计划, 一个需求版本对应一条记录

## [RDV0.1.1]
### 1. 结构化思考 (Structured Thinking)
- 需求分析 :
  
  - 核心目标 : 实现 RDV0.1.1 版本需求，即在 书写模式 (DRAW) 下，增加对 手指 (Finger) 触摸类型的特殊处理。
  - 前置条件 : 必须开启 手笔分离模式 (Finger Separate Mode) 。
  - 行为逻辑 :
    - Pen (笔) : 继续执行书写 (现有逻辑)。
    - HandEraser (手掌橡皮擦) : 继续执行擦除 (现有逻辑)。
    - Finger (手指) :
      - 若 手笔分离开启 : 命中元素则 移动元素 ，未命中则不书写（防误触/空操作）。
      - 若 手笔分离关闭 : 执行 书写 (默认逻辑)。
  - 并发要求 : 支持多指操作，即笔写、手擦、手指移动可以同时进行。这意味着我们不能简单地切换整个画板的状态，而是需要 基于触摸点 (Pointer) 来区分行为。
- 现有架构理解 :
  
  - 状态机 ( CanvasStateMachine ) : 采用并行状态机设计，分为 TOOL (工具), NAVIGATION (导航), MULTI_TOUCH (多指) 三个区域。
  - 当前状态 : 在书写模式下， TOOL 区域的活动状态是 PenState 。
  - PenState 职责 : 目前 PenState 不仅仅处理“笔”，实际上它已经接管了所有书写模式下的触摸事件。它内部已经实现了对 HandEraser 的并发处理（通过 handEraserMap 维护擦除状态）。
  - 触摸识别 : HikDefaultTouchCalc 已经支持 Pen , HandEraser , Finger 的类型识别。
### 2. 设计思路 (Design Strategy)
基于现有架构，最自然且符合“并行状态机”设计原则的方案是 扩展 PenState ，使其成为一个全能的“书写模式控制器”。

- 扩展 SDK 能力 :
  
  - 目前的 moveSelectedElements 是针对“被选中集合”的操作。为了支持多指并发移动（例如两根手指分别移动两个不同的元素），我们需要一种 不依赖全局选中状态 的移动能力。
  - 新增接口 : 在 IWhiteBoardSDK 中增加 moveElement(id: String, dx: Float, dy: Float) ，允许直接移动指定 ID 的元素。
- 改造 PenState :
  
  - 新增状态维护 : 类似于 handEraserMap ，新增 fingerMoveMap (Map<PointerId, MoveInfo>) 来跟踪当前正在进行“移动操作”的手指及其控制的元素 ID 和上一次位置。
  - 分发逻辑 ( handleTouchEvent ) :
    1. 获取当前触摸点的类型 ( TouchType ) 和模式 ( isFingerSeparateMode )。
    2. 分支判断 :
       - 是 HandEraser : 走现有擦除逻辑。
       - 是 Finger 且 手笔分离开启 :
         - ACTION_DOWN : 执行点击检测 ( queryElements )。
           - 若命中元素（取 zIndex 最高的）：记录 PointerId 与元素 ID 的绑定关系， 消费事件 （阻止后续书写逻辑）。
           - 若未命中： 消费事件 （阻止手指画出线条，实现“手指不写字”的效果）。
         - ACTION_MOVE : 若该 PointerId 在 fingerMoveMap 中，计算 delta，调用 sdk.moveElement 更新位置。
         - ACTION_UP : 提交移动操作 ( commitMove / saveToUndo )，清理 Map。
       - 其他情况 (Pen 或 手笔分离关闭) : 走现有书写逻辑 ( activePaths )。
### 3. 行动计划 (Action Plan)
1. SDK 层扩展 :
   
   - 修改 IWhiteBoardSDK.kt ，增加 moveElement(id: String, dx: Float, dy: Float) 接口。
   - 在 WhiteBoardSDKImpl.kt 中实现该接口，确保移动操作能正确更新 uiState 中的元素列表。
2. PenState 逻辑实现 :
   
   - 修改 PenState.kt ：
     - 定义数据结构 MovingState (记录 elementId, lastX, lastY)。
     - 新增 fingerMoveMap 变量。
     - 在 handleTouchEvent 的 ACTION_DOWN 中增加对 TouchType.Finger + isFingerSeparateMode 的判断与命中测试逻辑。
     - 在 ACTION_MOVE 中增加移动元素的逻辑。
     - 在 ACTION_UP 中增加清理和提交逻辑。
     - 确保在“手指移动”逻辑执行时，跳过后续的“书写”逻辑。

