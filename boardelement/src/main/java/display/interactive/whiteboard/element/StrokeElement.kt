package display.interactive.whiteboard.element

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

/**
 * Represents a stroke drawn with a pen, pencil, or marker.
 */
class StrokeElement(
    id: String,
    var points: List<PointF>,
    var color: Int,
    var strokeWidth: Float,
    val type: StrokeType,
    zIndex: Int
) : BaseElement(id = id, zIndex = zIndex) {

    enum class StrokeType {
        PEN, PENCIL, MARKER
    }

    private val bounds = RectF()
    private val path = Path()

    init {
        rebuildPath()
        addSelectedAbility(SelectedAbility.MOVE)
        addSelectedAbility(SelectedAbility.SCALE)
        addSelectedAbility(SelectedAbility.ROTATE)
        addSelectedAbility(SelectedAbility.CHANGE_COLOR)
        addSelectedAbility(SelectedAbility.DELETE)
        addSelectedAbility(SelectedAbility.COPY)
        addSelectedAbility(SelectedAbility.CHANGE_ORDER)
    }

    fun rebuildPath() {
        path.reset()
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
        }
        val tempBounds = RectF()
        path.computeBounds(tempBounds, true)
        bounds.set(tempBounds)
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        paint.reset()
        paint.color = color
        paint.strokeWidth = strokeWidth
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        
        when (type) {
            StrokeType.PEN -> paint.alpha = 255
            StrokeType.PENCIL -> paint.alpha = 200
            StrokeType.MARKER -> paint.alpha = 128
        }
        
        applyTransform(canvas)
        canvas.drawPath(path, paint)
        restoreTransform(canvas)
    }

    override fun copy(): BaseElement {
        val newStroke = StrokeElement(
            id = java.util.UUID.randomUUID().toString(),
            points = points.map { android.graphics.PointF(it.x, it.y) },
            color = color,
            strokeWidth = strokeWidth,
            type = type,
            zIndex = zIndex
        )
        newStroke.x = x
        newStroke.y = y
        newStroke.scaleX = scaleX
        newStroke.scaleY = scaleY
        newStroke.rotation = rotation
        // Copy abilities
        SelectedAbility.values().forEach { ability ->
            if (this.hasSelectedAbility(ability)) {
                newStroke.addSelectedAbility(ability)
            }
        }
        return newStroke
    }

    override fun getBounds(): RectF {
        val rect = RectF(
            x + bounds.left * scaleX,
            y + bounds.top * scaleY,
            x + bounds.right * scaleX,
            y + bounds.bottom * scaleY
        )
        // Note: Simple bounds without rotation for now
        return rect
    }

    override fun erase(ex: Float, ey: Float, radius: Float): List<BaseElement> {
        // Convert eraser world coordinates to local coordinates
        // Note: Simplified, doesn't account for rotation yet
        val localEx = (ex - x) / scaleX
        val localEy = (ey - y) / scaleY
        val localRadius = radius / maxOf(scaleX, scaleY)

        val remainingSegments = mutableListOf<MutableList<PointF>>()
        var currentSegment = mutableListOf<PointF>()
        
        val radiusSq = localRadius * localRadius

        for (point in points) {
            val dx = point.x - localEx
            val dy = point.y - localEy
            val distSq = dx * dx + dy * dy
            
            if (distSq > radiusSq) {
                // Point is outside eraser
                currentSegment.add(point)
            } else {
                // Point is inside eraser, split segment
                if (currentSegment.isNotEmpty()) {
                    remainingSegments.add(currentSegment)
                    currentSegment = mutableListOf<PointF>()
                }
            }
        }
        
        if (currentSegment.isNotEmpty()) {
            remainingSegments.add(currentSegment)
        }

        return remainingSegments.map { segmentPoints ->
            // For new segments, we can keep the same x, y, scale, rotation
            val newStroke = StrokeElement(
                id = java.util.UUID.randomUUID().toString(),
                points = segmentPoints,
                color = color,
                strokeWidth = strokeWidth,
                type = type,
                zIndex = zIndex
            )
            newStroke.x = x
            newStroke.y = y
            newStroke.scaleX = scaleX
            newStroke.scaleY = scaleY
            newStroke.rotation = rotation
            newStroke
        }
    }
}
