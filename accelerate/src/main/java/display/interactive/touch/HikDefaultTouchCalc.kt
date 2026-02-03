package display.interactive.touch

/**
 * @author lvhan5
 * @date 2026/2/3
 * @version v1.0.0
 * 默认的设备参数
 */
object HikDefaultTouchCalc: ITouchCalculator() {

    override val devicePhyHeight: Float
        get() = 841.3f

    override val devicePhyWidth: Float
        get() = 1465.3f

    override fun transformMajor2MMFactor(): Float {
        return devicePhyWidth / 32767
    }

    override fun transformPixel2MMFactor(): Float {
        return devicePhyWidth / 3840
    }

    override fun transformTouchMajor2MM(major: Float): Float {
        return super.transformTouchMajor2MM(major)
    }

    override fun getTouchType(major: Float): TouchType {
        val mm = transformTouchMajor2MM(major)
        return if (mm < 4) {
            TouchType.Pen
        } else if (mm > 40) {
            TouchType.Eraser
        } else {
            TouchType.Finger
        }
    }
}
