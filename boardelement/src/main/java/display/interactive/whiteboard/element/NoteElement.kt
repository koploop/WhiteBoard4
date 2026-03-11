package display.interactive.whiteboard.element

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import java.util.UUID

class NoteElement(
    id: String,
    var elementWidth: Float = 320f,
    var elementHeight: Float = 220f,
    var backgroundColor: Int = 0xFFFFF59D.toInt(),
    var textColor: Int = Color.BLACK,
    var text: String = "",
    zIndex: Int
) : BaseElement(id = id, zIndex = zIndex), NativeViewElement {

    data class AnnotationStroke(
        val id: String = UUID.randomUUID().toString(),
        var points: MutableList<PointF> = mutableListOf(),
        var color: Int = Color.RED,
        var strokeWidth: Float = 4f
    )

    private val annotationStrokes = mutableListOf<AnnotationStroke>()
    private val activeStrokeByPointer = mutableMapOf<Int, AnnotationStroke>()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val annotationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    init {
        addSelectedAbility(SelectedAbility.MOVE)
        addSelectedAbility(SelectedAbility.SCALE)
        addSelectedAbility(SelectedAbility.CHANGE_COLOR)
        addSelectedAbility(SelectedAbility.EDIT_TEXT)
        addSelectedAbility(SelectedAbility.ANNOTATE)
        addSelectedAbility(SelectedAbility.ERASE_ANNOTATION)
        addSelectedAbility(SelectedAbility.DELETE)
        addSelectedAbility(SelectedAbility.COPY)
        addSelectedAbility(SelectedAbility.CHANGE_ORDER)
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        applyTransform(canvas)
        backgroundPaint.color = backgroundColor
        canvas.drawRect(0f, 0f, elementWidth, elementHeight, backgroundPaint)
        annotationStrokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            annotationPaint.color = stroke.color
            annotationPaint.strokeWidth = stroke.strokeWidth
            val path = Path()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x, stroke.points[i].y)
            }
            canvas.drawPath(path, annotationPaint)
        }
        restoreTransform(canvas)
    }

    override fun copy(): BaseElement {
        val newElement = NoteElement(
            id = UUID.randomUUID().toString(),
            elementWidth = elementWidth,
            elementHeight = elementHeight,
            backgroundColor = backgroundColor,
            textColor = textColor,
            text = text,
            zIndex = zIndex
        )
        newElement.x = x
        newElement.y = y
        newElement.scaleX = scaleX
        newElement.scaleY = scaleY
        newElement.rotation = rotation
        annotationStrokes.forEach { stroke ->
            newElement.annotationStrokes.add(
                AnnotationStroke(
                    points = stroke.points.map { PointF(it.x, it.y) }.toMutableList(),
                    color = stroke.color,
                    strokeWidth = stroke.strokeWidth
                )
            )
        }
        SelectedAbility.values().forEach { ability ->
            if (hasSelectedAbility(ability)) {
                newElement.addSelectedAbility(ability)
            }
        }
        return newElement
    }

    override fun getBounds(): RectF {
        return RectF(
            x,
            y,
            x + elementWidth * scaleX,
            y + elementHeight * scaleY
        )
    }

    override fun contains(px: Float, py: Float): Boolean {
        val inverse = Matrix()
        if (!getTransformMatrix().invert(inverse)) {
            return false
        }
        val pts = floatArrayOf(px, py)
        inverse.mapPoints(pts)
        return pts[0] in 0f..elementWidth && pts[1] in 0f..elementHeight
    }

    fun beginAnnotation(pointerId: Int, worldX: Float, worldY: Float, color: Int, strokeWidth: Float) {
        val local = worldToLocal(worldX, worldY) ?: return
        if (local.x !in 0f..elementWidth || local.y !in 0f..elementHeight) return
        val stroke = AnnotationStroke(color = color, strokeWidth = strokeWidth)
        stroke.points.add(local)
        activeStrokeByPointer[pointerId] = stroke
    }

    fun appendAnnotation(pointerId: Int, worldX: Float, worldY: Float) {
        val stroke = activeStrokeByPointer[pointerId] ?: return
        val local = worldToLocal(worldX, worldY) ?: return
        stroke.points.add(local)
    }

    fun endAnnotation(pointerId: Int) {
        val stroke = activeStrokeByPointer.remove(pointerId) ?: return
        if (stroke.points.size >= 2) {
            annotationStrokes.add(stroke)
        }
    }

    fun updateText(newText: String) {
        text = newText
    }

    override fun erase(ex: Float, ey: Float, radius: Float): List<BaseElement> {
        if (annotationStrokes.isEmpty()) return listOf(this)
        val local = worldToLocal(ex, ey) ?: return listOf(this)
        val localRadius = radius / maxOf(scaleX, scaleY)
        val radiusSq = localRadius * localRadius
        var changed = false
        val newStrokes = mutableListOf<AnnotationStroke>()
        annotationStrokes.forEach { stroke ->
            if (stroke.points.isEmpty()) {
                return@forEach
            }
            var current = mutableListOf<PointF>()
            stroke.points.forEach { point ->
                val dx = point.x - local.x
                val dy = point.y - local.y
                if (dx * dx + dy * dy > radiusSq) {
                    current.add(point)
                } else {
                    if (current.size >= 2) {
                        newStrokes.add(
                            AnnotationStroke(
                                points = current.toMutableList(),
                                color = stroke.color,
                                strokeWidth = stroke.strokeWidth
                            )
                        )
                    }
                    current = mutableListOf()
                    changed = true
                }
            }
            if (current.size >= 2) {
                newStrokes.add(
                    AnnotationStroke(
                        points = current.toMutableList(),
                        color = stroke.color,
                        strokeWidth = stroke.strokeWidth
                    )
                )
            }
        }
        if (changed) {
            annotationStrokes.clear()
            annotationStrokes.addAll(newStrokes)
        }
        return listOf(this)
    }

    fun clearActiveAnnotation(pointerId: Int) {
        activeStrokeByPointer.remove(pointerId)
    }

    private fun worldToLocal(worldX: Float, worldY: Float): PointF? {
        val inverse = Matrix()
        if (!getTransformMatrix().invert(inverse)) {
            return null
        }
        val pts = floatArrayOf(worldX, worldY)
        inverse.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }
}
