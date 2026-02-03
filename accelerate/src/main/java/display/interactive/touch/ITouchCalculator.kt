package display.interactive.touch

/**
 * @author lvhan5
 * @date 2026/2/3
 * @version v1.0.0
 * 触摸点大小计算器
 */
abstract class ITouchCalculator {

    /**
     * 设备物理宽高
     */
    open val devicePhyWidth = 0f
    open val devicePhyHeight = 0f

    /**
     * touchMajor数值转换成毫米的转换值
     * 不同的设备可能不一样
     */
    open fun transformMajor2MMFactor(): Float {
        return 0f
    }

    /**
     * 像素值转换成毫米的转换值
     */
    open fun transformPixel2MMFactor(): Float {
        return 0f
    }

    /**
     * 将touchMajor值转为mm毫米
     */
    open fun transformTouchMajor2MM(major: Float): Float {
        return major * transformMajor2MMFactor()
    }

    /**
     * 获取当前触摸点的类型
     */
    open fun getTouchType(major: Float): TouchType {
        return TouchType.Pen
    }

}

enum class TouchType(val type: Int) {
    // 书写笔
    Pen(0),
    // 手指触摸
    Finger(1),
    // 橡皮擦
    Eraser(2)
}
