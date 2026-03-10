package display.interactive.whiteboard.element

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import java.util.UUID

class ImageElement(
    id: String,
    val bitmap: Bitmap,
    var elementWidth: Float = bitmap.width.toFloat(),
    var elementHeight: Float = bitmap.height.toFloat(),
    zIndex: Int
) : BaseElement(id = id, zIndex = zIndex) {

    init {
        addSelectedAbility(SelectedAbility.MOVE)
        addSelectedAbility(SelectedAbility.SCALE)
        addSelectedAbility(SelectedAbility.ROTATE)
        addSelectedAbility(SelectedAbility.CHANGE_ORDER)
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        applyTransform(canvas)
        // Draw centered at (0,0) so that (x,y) becomes the center
        val halfW = elementWidth / 2f
        val halfH = elementHeight / 2f
        val dst = RectF(-halfW, -halfH, halfW, halfH)
        canvas.drawBitmap(bitmap, null, dst, paint)
        restoreTransform(canvas)
    }

    override fun copy(): BaseElement {
        val newImage = ImageElement(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap,
            elementWidth = elementWidth,
            elementHeight = elementHeight,
            zIndex = zIndex
        )
        newImage.x = x
        newImage.y = y
        newImage.scaleX = scaleX
        newImage.scaleY = scaleY
        newImage.rotation = rotation
        SelectedAbility.values().forEach { ability ->
            if (this.hasSelectedAbility(ability)) {
                newImage.addSelectedAbility(ability)
            }
        }
        return newImage
    }

    override fun getBounds(): RectF {
        // Return bounding box centered at (x,y)
        val halfW = elementWidth * scaleX / 2f
        val halfH = elementHeight * scaleY / 2f
        return RectF(
            x - halfW,
            y - halfH,
            x + halfW,
            y + halfH
        )
    }

    override fun contains(px: Float, py: Float): Boolean {
        val inverse = Matrix()
        if (!getTransformMatrix().invert(inverse)) {
            return false
        }
        val pts = floatArrayOf(px, py)
        inverse.mapPoints(pts)
        // Check against local centered coordinates
        val halfW = elementWidth / 2f
        val halfH = elementHeight / 2f
        return pts[0] in -halfW..halfW && pts[1] in -halfH..halfH
    }

    override fun erase(ex: Float, ey: Float, radius: Float): List<BaseElement> {
        return listOf(this)
    }
}
