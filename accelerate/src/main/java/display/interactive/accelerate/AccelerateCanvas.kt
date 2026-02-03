package display.interactive.accelerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import androidx.core.graphics.createBitmap

/**
 * @author lvhan5
 * @date 2026/1/30
 * @version v1.0.0
 * dscription here
 */
class AccelerateCanvas(val context: Context, val size: Size) {

    private var accelerateAddr: Long = AccelerateAddr.getAddressByJar()

    lateinit var accelerateBitmap: HBitmap

    lateinit var accelerateCanvas: Canvas

    init {
        accelerateBitmap = getBitmap(size.width, size.height)
        accelerateBitmap.getCommonBitmap()?.let {
            accelerateCanvas = Canvas(it)
        } ?: {
            accelerateCanvas = Canvas(createBitmap(size.width, size.height))
        }
    }

    /**
     * @description 获取加速位图的列表
     *
     * @author chenguofan
     * @return 加速位图列表
     * @date 2024/3/21
     */
    fun getBitmap(width: Int, height: Int): HBitmap {
        if (!::accelerateBitmap.isInitialized) {
            accelerateBitmap = getCurrentBitmap(width, height)
        }
        return accelerateBitmap
    }

    /**
     * @description 获取当前正在显示的加速位图
     *
     * @author chenguofan
     * @return 加速位图
     * @date 2024/3/21
     */
    private fun getCurrentBitmap(width: Int, height: Int): HBitmap {
        if (::accelerateBitmap.isInitialized) {
            return accelerateBitmap
        }

        if (accelerateAddr == 0L) {
            val bitmap = createBitmap(width, height)
            return HBitmap(bitmap)
        }

        val format = HBitmap.FORMAT_ARGB_8888
        return HBitmap(accelerateAddr, width, height, format)
    }
}