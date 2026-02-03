package display.interactive.whiteboard.element

import android.graphics.PointF

/**
 * The complete state of the whiteboard.
 */
data class WhiteBoardState(
    val elements: List<BaseElement> = emptyList(),
    val backgroundState: BackgroundState = BackgroundState(),
    val selectedElementIds: Set<String> = emptySet(),
    val interactionMode: InteractionMode = InteractionMode.DRAW,
    val currentStrokeType: StrokeElement.StrokeType = StrokeElement.StrokeType.PEN,
    val currentStrokeColor: Int = 0xFF000000.toInt(),
    val currentStrokeWidth: Float = 5f,
    val isMultiFingerEnabled: Boolean = false,
    val isZoomMode: Boolean = true, // V0.0.7 Zoom/Pan mode
    val canNavigate: Boolean = true, // Navigation (pan/zoom)
    val canvasScale: Float = 1f,
    val canvasOffsetX: Float = 0f,
    val canvasOffsetY: Float = 0f,
    val eraserPosition: PointF? = null
)

enum class InteractionMode {
    DRAW, SELECT, NAVIGATE, ERASER
}
