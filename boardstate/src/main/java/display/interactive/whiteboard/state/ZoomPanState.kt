package display.interactive.whiteboard.state

import android.util.Log
import android.view.MotionEvent

/**
 * Handles zooming and panning of the canvas.
 */
class ZoomPanState : ICanvasState {
    companion object {
        private const val TAG = "ZoomPanState"
        private const val POINTER_TIMEOUT = 100L
        private const val ZOOM_THRESHOLD = 10f
        private const val PAN_THRESHOLD = 5f
    }

    private var initialPointersTime = 0L
    private var isZooming = false
    private var isPanning = false
    
    private var startZoomDistance = 0f
    private var startZoomCenterX = 0f
    private var startZoomCenterY = 0f
    
    private var lastZoomDistance = 0f
    private var lastZoomCenterX = 0f
    private var lastZoomCenterY = 0f

    override fun handleTouchEvent(event: MotionEvent, pointerId: Int, context: StateContext): Boolean {
        val uiState = context.sdk.uiState.value
        if (!uiState.isZoomMode) return false

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            initialPointersTime = System.currentTimeMillis()
        }

        if (event.pointerCount < 2 && !isZooming && !isPanning) return false

        val currentTime = System.currentTimeMillis()
        val isSimultaneous = currentTime - initialPointersTime < POINTER_TIMEOUT

        if (!(isSimultaneous || isZooming || isPanning)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    startZoomDistance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    startZoomCenterX = (event.getX(0) + event.getX(1)) / 2
                    startZoomCenterY = (event.getY(0) + event.getY(1)) / 2
                    
                    lastZoomDistance = startZoomDistance
                    lastZoomCenterX = startZoomCenterX
                    lastZoomCenterY = startZoomCenterY
                    
                    isZooming = false
                    isPanning = false
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val centerX = (event.getX(0) + event.getX(1)) / 2
                    val centerY = (event.getY(0) + event.getY(1)) / 2

                    if (!isZooming && !isPanning) {
                        val distDelta = Math.abs(distance - startZoomDistance)
                        val panDeltaX = Math.abs(centerX - startZoomCenterX)
                        val panDeltaY = Math.abs(centerY - startZoomCenterY)
                        
                        if (distDelta > ZOOM_THRESHOLD) {
                            isZooming = true
                        } else if (panDeltaX > PAN_THRESHOLD || panDeltaY > PAN_THRESHOLD) {
                            isPanning = true
                        }
                    }

                    if (isZooming) {
                        if (lastZoomDistance > 0) {
                            val scaleFactor = distance / lastZoomDistance
                            val newScale = (uiState.canvasScale * scaleFactor).coerceIn(0.1f, 10f)
                            
                            val actualScaleFactor = newScale / uiState.canvasScale
                            val newOffsetX = centerX - (centerX - uiState.canvasOffsetX) * actualScaleFactor
                            val newOffsetY = centerY - (centerY - uiState.canvasOffsetY) * actualScaleFactor
                            
                            context.sdk.setCanvasTransform(newScale, newOffsetX, newOffsetY)
                        }
                    } else if (isPanning) {
                        val panX = centerX - lastZoomCenterX
                        val panY = centerY - lastZoomCenterY
                        context.sdk.setCanvasTransform(uiState.canvasScale, uiState.canvasOffsetX + panX, uiState.canvasOffsetY + panY)
                    }

                    lastZoomDistance = distance
                    lastZoomCenterX = centerX
                    lastZoomCenterY = centerY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    lastZoomDistance = 0f
                    isZooming = false
                    isPanning = false
                }
                return true
            }
        }
        return false
    }

    fun reset() {
        isZooming = false
        isPanning = false
        lastZoomDistance = 0f
    }
}
