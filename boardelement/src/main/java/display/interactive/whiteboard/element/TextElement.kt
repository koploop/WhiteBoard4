package display.interactive.whiteboard.element

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Represents a text element on the whiteboard.
 */
class TextElement(
    id: String,
    var text: String,
    var fontSize: Float,
    var color: Int,
    x: Float,
    y: Float,
    zIndex: Int
) : BaseElement(id = id, x = x, y = y, zIndex = zIndex) {

    init {
        addSelectedAbility(SelectedAbility.MOVE)
        addSelectedAbility(SelectedAbility.SCALE)
        addSelectedAbility(SelectedAbility.ROTATE)
        addSelectedAbility(SelectedAbility.CHANGE_COLOR)
        addSelectedAbility(SelectedAbility.DELETE)
        addSelectedAbility(SelectedAbility.COPY)
        addSelectedAbility(SelectedAbility.CHANGE_ORDER)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        textPaint.color = color
        textPaint.textSize = fontSize
        
        applyTransform(canvas)
        canvas.drawText(text, 0f, 0f, textPaint)
        restoreTransform(canvas)
    }

    override fun getBounds(): RectF {
        textPaint.textSize = fontSize
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        
        val rect = RectF(
            x + textBounds.left * scaleX,
            y + textBounds.top * scaleY,
            x + textBounds.right * scaleX,
            y + textBounds.bottom * scaleY
        )
        // Note: This doesn't account for rotation yet, but is enough for basic bounds
        return rect
    }
}
