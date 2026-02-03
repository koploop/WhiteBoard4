package display.interactive.whiteboard.element

import android.graphics.Bitmap

/**
 * Represents the state of the whiteboard background.
 */
data class BackgroundState(
    val type: BackgroundType = BackgroundType.GRID,
    val color: Int = 0xFFFFFFFF.toInt(),
    val image: Bitmap? = null,
    val gridSpacing: Float = 50f,
    val gridColor: Int = 0xFFE0E0E0.toInt()
)

enum class BackgroundType {
    SOLID, GRID, IMAGE
}
