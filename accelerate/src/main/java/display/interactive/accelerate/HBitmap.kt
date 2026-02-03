package display.interactive.accelerate

import android.graphics.Bitmap
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 *@ClassName HBitmap
 *@Description
 *@Author zhaoyang40
 *@Date 2025/8/12 14:13
 */
class HBitmap {

    companion object {

        const val TAG = "HBitmap"

        /**
         * 像素格式argb8888
         */
        const val FORMAT_ARGB_8888 = 0

        /**
         * 像素格式abgr8888
         */
        const val FORMAT_ABGR_8888 = 1

        /**
         * 像素格式rgb565
         */
        const val FORMAT_RBG_565 = 2

        /**
         * 像素格式abgr4444
         */
        const val FORMAT_ARGB_4444 = 3

        /**
         * 像素格式alpha8
         */
        const val FORMAT_ALPHA_8 = 4


        /**
         * 通过内存地址创建一个标准位图
         *
         * @param address   内存首地址
         * @param width  位图宽度
         * @param height 位图高度
         * @return Bitmap 标准的位图对象，可能为null
         */
        fun createBitmap(address: Long, width: Int, height: Int): Bitmap? {
            var bitmap: Bitmap? = null
            var createBitmapMethod: Method? = null
            try {
                createBitmapMethod = Bitmap::class.java.getMethod(
                    "createBitmap",
                    Int::class.java,
                    Int::class.java,
                    Bitmap.Config::class.java,
                    Long::class.java
                )
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            }
            try {
                bitmap = createBitmapMethod?.invoke(
                    null,
                    width,
                    height,
                    Bitmap.Config.ARGB_8888,
                    address
                ) as Bitmap?
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            }
            return bitmap
        }
    }

    /**
     * 标准位图
     */
    private var mBitmap: Bitmap?

    /**
     * 像素格式
     */
    private var mFormat: Int

    /**
     * 位图宽度
     */
    private val mWidth: Int

    /**
     * 位图高度
     */
    private val mHeight: Int

    /**
     * 构造函数,根据一块内存地址进行一个位图创建
     *
     * @param address   内存首地址
     * @param width  位图宽度
     * @param height 位图高度
     * @param format 像素格式
     */
    constructor(address: Long, width: Int, height: Int, format: Int) {
        mFormat = format
        mBitmap = createBitmap(address, width, height)
        mWidth = width
        mHeight = height
    }

    /**
     * 构造函数,直接持有一个标准位图
     *
     * @param bitmap 标准位图
     */
    constructor(bitmap: Bitmap) {
        mBitmap = bitmap
        mWidth = bitmap.width
        mHeight = bitmap.height
        mFormat = when (bitmap.config) {
            Bitmap.Config.ARGB_8888 -> FORMAT_ARGB_8888
            Bitmap.Config.RGB_565 -> FORMAT_RBG_565
            Bitmap.Config.ARGB_4444 -> FORMAT_ARGB_4444
            else -> Log.d(TAG, "Unsupported bitmap format.")
        }
    }

    /**
     * 获取标准的位图
     *
     * @return Bitmap 标准位图对象
     */
    fun getCommonBitmap(): Bitmap? {
        return mBitmap
    }

    /**
     * 获取宽度
     *
     * @return Int 位图宽度
     */
    fun getWidth(): Int {
        return mWidth
    }

    /**
     * 获取高度
     *
     * @return Int 触控点高度
     */
    fun getHeight(): Int {
        return mHeight
    }

    /**
     * 获取高度
     *
     * @return Int 像素格式
     */
    fun getFormat(): Int {
        return mFormat
    }
}