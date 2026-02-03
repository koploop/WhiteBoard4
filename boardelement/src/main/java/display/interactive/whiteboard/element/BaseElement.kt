package display.interactive.whiteboard.element

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.UUID

/**
 * Base class for all whiteboard elements.
 */
abstract class BaseElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 0f,
    var y: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var rotation: Float = 0f,
    var isSelected: Boolean = false,
    var zIndex: Int = 0
) {
    private val selectedAbilities = mutableSetOf<SelectedAbility>()

    /**
     * Callback for when the element is being manipulated while selected.
     */
    interface OnSelectedTransformListener {
        fun onMove(dx: Float, dy: Float)
        fun onScale(scaleX: Float, scaleY: Float)
        fun onRotate(rotation: Float)
    }

    var transformListener: OnSelectedTransformListener? = null

    /**
     * Add an ability to the element when it is selected.
     */
    fun addSelectedAbility(ability: SelectedAbility) {
        selectedAbilities.add(ability)
    }

    /**
     * Check if the element has a specific selected ability.
     */
    fun hasSelectedAbility(ability: SelectedAbility): Boolean {
        return selectedAbilities.contains(ability)
    }

    /**
     * Draw the element on the canvas.
     */
    abstract fun draw(canvas: Canvas, paint: Paint)

    /**
     * Get the bounding box of the element in world coordinates.
     */
    abstract fun getBounds(): RectF

    /**
     * Get the bounding box of the element in world coordinates, including rotation.
     */
    open fun getRotatedBounds(): android.graphics.RectF {
        val bounds = getBounds()
        if (rotation == 0f) return bounds
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation, x, y)
        val rotatedBounds = android.graphics.RectF()
        matrix.mapRect(rotatedBounds, bounds)
        return rotatedBounds
    }

    /**
     * Check if a point (in world coordinates) is inside the element.
     */
    open fun contains(px: Float, py: Float): Boolean {
        return getBounds().contains(px, py)
    }

    /**
     * Erase parts of the element that intersect with the eraser at (ex, ey) with radius.
     * Returns a list of new elements that remain after erasure.
     */
    open fun erase(ex: Float, ey: Float, radius: Float): List<BaseElement> {
        return if (contains(ex, ey)) emptyList() else listOf(this)
    }

    /**
     * Apply transformation to the canvas before drawing.
     */
    protected fun applyTransform(canvas: Canvas) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.scale(scaleX, scaleY)
    }

    protected fun restoreTransform(canvas: Canvas) {
        canvas.restore()
    }
}
