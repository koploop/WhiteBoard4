package display.interactive.whiteboard.element

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Represents an image element on the whiteboard.
 */
class ImageElement(
    id: String,
    val bitmap: Bitmap,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    zIndex: Int
) : BaseElement(id = id, x = x, y = y, zIndex = zIndex) {

    init {
        scaleX = width / bitmap.width
        scaleY = height / bitmap.height
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        applyTransform(canvas)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        restoreTransform(canvas)
    }

    override fun getBounds(): RectF {
        return RectF(
            x,
            y,
            x + bitmap.width * scaleX,
            y + bitmap.height * scaleY
        )
    }
}
