package display.interactive.accelerate

import android.util.Log

/**
 * @author lvhan5
 * @date 2026/1/30
 * @version v1.0.0
 * 加速图层获取
 */
object AccelerateAddr {

    private val TAG = "AccelerateAddr"

    /**
     * 反射获取加速位图地址的方方法
     * @return
     */
    fun getAddressByJar(): Long {
        var address: Long = 0
        try {
            val implClass = Class.forName("com.hikvision.media.impl.PaintAccelerateManagerImpl")
            val getAddressMethod = implClass.getMethod("getAddress")
            val constructor = implClass.getDeclaredConstructor()
            val manager = constructor.newInstance()
            address = getAddressMethod.invoke(manager) as Long
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return address
    }

    /**
     * 反射清除加速位图的方法
     */
    fun clearVopByJar() {
        Log.d(TAG, "enter clearVopByJar")
        val start = System.currentTimeMillis()
        try {
            val implClass = Class.forName("com.hikvision.media.impl.PaintAccelerateManagerImpl")
            val clearMethod = implClass.getMethod("clear")
            val constructor = implClass.getDeclaredConstructor()
            val manager = constructor.newInstance()
            clearMethod.invoke(manager)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        Log.d(TAG, "end clearVopByJar: cost = ${System.currentTimeMillis() - start}")
    }

}