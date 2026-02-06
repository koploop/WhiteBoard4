mindmap
  root((画板触摸点处理逻辑))
    "画布缩放开关"
      "开启"
        "是否符合移动/缩放画布规则"
          "符合"
            "移动/缩放画布"
          "不符合"
            "只处理第一个触摸点，按单个触摸点逻辑处理"
      "关闭"
        "单个触摸点"
          "手笔分离模式"
            "关闭"
              "橡皮擦模式"
                "擦除"
              "圈选模式下"
                "圈选"
              "书写模式"
                "触摸点类型是否手掌"
                  "是手掌"
                    "手掌擦除"
                  "非手掌"
                    "书写"
            "开启"
              "不再支持切换到橡皮擦模式/圈选模式"
              "触摸点类型是书写笔"
                "书写"
              "触摸点类型是手指"
                "默认是移动，也支持配置为擦除"
              "触摸点类型是手掌"
                "手掌擦除"
        "多个触摸点"
          "手笔分离模式"
            "关闭"
              "橡皮擦模式"
                "擦除，只处理第一个手指"
              "圈选模式下"
                "圈选，只处理第一个手指"
              "书写模式"
                "允许多指同时书写和手掌擦除，根据触摸点类型执行对应动作"
                  "是手掌"
                    "手掌擦除"
                  "非手掌"
                    "书写"
            "开启"
              "不再支持切换到橡皮擦模式/圈选模式"
              "触摸点类型是书写笔"
                "书写"
              "触摸点类型是手指"
                默认是移动，也支持配置为擦除"
              "触摸点类型是手掌"
                "手掌擦除"
              "允许同时存在书写笔、手指、手掌三个动作。默认情况下只允许同时存在书写笔书写和手掌擦除，通过修改默认配置参数可以放开同时存在书写笔、手指、手掌三个动作"

```mermaid
flowchart TD
    Start([触摸事件]) --> CheckZoom{画布缩放开关开启?}
    
    %% 缩放开启分支
    CheckZoom -- 是 --> CheckRules{符合移动/缩放规则?}
    CheckRules -- 是 --> ActionZoom[移动/缩放画布]
    CheckRules -- 否 --> SingleTouchLogic
    
    %% 缩放关闭分支
    CheckZoom -- 否 --> CheckCount{触摸点数量}
    
    %% 单点触摸逻辑
    CheckCount -- 单个 --> SingleTouchLogic{手笔分离模式开启?}
    
    SingleTouchLogic -- 否 --> CheckModeSingle{当前模式}
    CheckModeSingle -- 橡皮擦 --> ActionErase[擦除]
    CheckModeSingle -- 圈选 --> ActionLasso[圈选]
    CheckModeSingle -- 书写 --> CheckPalmSingle{是否手掌?}
    CheckPalmSingle -- 是 --> ActionHandErase1[手掌擦除]
    CheckPalmSingle -- 否 --> ActionWrite1[书写]
    
    SingleTouchLogic -- 是 --> CheckTypeSingle{触摸点类型}
    CheckTypeSingle -- 书写笔 --> ActionWrite2[书写]
    CheckTypeSingle -- 手指 --> ActionMove1[移动 (默认) / 擦除 (配置)]
    CheckTypeSingle -- 手掌 --> ActionHandErase2[手掌擦除]
    
    %% 多点触摸逻辑
    CheckCount -- 多个 --> MultiTouchLogic{手笔分离模式开启?}
    
    MultiTouchLogic -- 否 --> CheckModeMulti{当前模式}
    CheckModeMulti -- 橡皮擦 --> ActionEraseFirst[擦除 (仅处理第一个手指)]
    CheckModeMulti -- 圈选 --> ActionLassoFirst[圈选 (仅处理第一个手指)]
    CheckModeMulti -- 书写 --> MultiSimul1[多指并发: 书写/手掌擦除]
    MultiSimul1 --> CheckPalmMulti1{对每个点: 是否手掌?}
    CheckPalmMulti1 -- 是 --> ActionHandErase3[手掌擦除]
    CheckPalmMulti1 -- 否 --> ActionWrite3[书写]
    
    MultiTouchLogic -- 是 --> MultiSimul2[多指并发操作]
    MultiSimul2 --> CheckTypeMulti{对每个点: 触摸类型}
    CheckTypeMulti -- 书写笔 --> ActionWrite4[书写]
    CheckTypeMulti -- 手指 --> ActionMove2[移动 (默认) / 擦除 (配置)]
    CheckTypeMulti -- 手掌 --> ActionHandErase4[手掌擦除]
    
    subgraph Concurrency[并发配置]
    MultiSimul2 -.-> NoteConfig[默认: 书写+手掌擦除<br>配置可开: 书写+手指+手掌]
    end
```
