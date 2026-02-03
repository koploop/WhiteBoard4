package display.interactive.whiteboard.impl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import display.interactive.accelerate.AccelerateCanvas
import display.interactive.whiteboard.element.*

/**
 * Custom SurfaceView for hardware-accelerated drawing and interacting with the whiteboard.
 */
class WhiteBoardSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback, Runnable {

    private var sdk: WhiteBoardSDKImpl? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val selectedGroupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val eraserIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 180
    }

    private var uiState: WhiteBoardState = WhiteBoardState()
    private var touchHandler: TouchHandler? = null
    private var selectedHandler: SelectedHandler? = null
    private var accelerateCanvas: AccelerateCanvas? = null

    private var isRunning = false
    private var renderThread: Thread? = null

    init {
        holder.addCallback(this)
    }

    fun setSDK(sdk: WhiteBoardSDKImpl) {
        this.sdk = sdk
        this.touchHandler = TouchHandler(sdk)
        this.selectedHandler = SelectedHandler(sdk)
        this.touchHandler?.setSelectedHandler(this.selectedHandler!!)
        this.accelerateCanvas?.let { touchHandler?.setAccelerateCanvas(it) }
    }

    fun setAccelerateCanvas(canvas: AccelerateCanvas) {
        this.accelerateCanvas = canvas
        this.touchHandler?.setAccelerateCanvas(canvas)
    }

    fun updateState(state: WhiteBoardState) {
        this.uiState = state
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        renderThread = Thread(this)
        renderThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        try {
            renderThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun run() {
        while (isRunning) {
            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try {
                    drawEverything(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            Thread.sleep(16)
        }
    }

    private fun drawEverything(canvas: Canvas) {
        drawBackground(canvas)

        canvas.save()
        canvas.translate(uiState.canvasOffsetX, uiState.canvasOffsetY)
        canvas.scale(uiState.canvasScale, uiState.canvasScale)

        // Draw existing elements
        uiState.elements.sortedBy { it.zIndex }.forEach { element ->
            element.isSelected = element.id in uiState.selectedElementIds
            element.draw(canvas, paint)
        }

        // Draw selection box and handles via SelectedHandler
        selectedHandler?.draw(canvas, uiState.elements, uiState.selectedElementIds, uiState.canvasScale)

        // Draw active paths (Real-time drawing)
        touchHandler?.activePaths?.values?.forEach { path ->
            paint.reset()
            paint.color = uiState.currentStrokeColor
            paint.strokeWidth = uiState.currentStrokeWidth
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND

            when (uiState.currentStrokeType) {
                StrokeElement.StrokeType.PEN -> paint.alpha = 255
                StrokeElement.StrokeType.PENCIL -> paint.alpha = 200
                StrokeElement.StrokeType.MARKER -> paint.alpha = 128
            }
            canvas.drawPath(path, paint)
        }

        // Draw selection rectangle (Lasso selection)
        touchHandler?.selectionRect?.let { rect ->
            canvas.drawRect(rect, selectionPaint)
        }

        // Draw Eraser Icon
        if (uiState.interactionMode == InteractionMode.ERASER) {
            uiState.eraserPosition?.let { pos ->
                canvas.drawCircle(pos.x, pos.y, 20f, eraserIconPaint)
            }
        }

        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas) {
        val bg = uiState.backgroundState
        when (bg.type) {
            BackgroundType.SOLID -> canvas.drawColor(bg.color)
            BackgroundType.GRID -> {
                canvas.drawColor(bg.color)
                drawGrid(canvas, bg)
            }
            BackgroundType.IMAGE -> {
                bg.image?.let {
                    canvas.drawBitmap(it, 0f, 0f, paint)
                } ?: canvas.drawColor(bg.color)
            }
        }
    }

    private fun drawGrid(canvas: Canvas, bg: BackgroundState) {
        gridPaint.color = bg.gridColor
        val spacing = bg.gridSpacing * uiState.canvasScale
        val offsetX = uiState.canvasOffsetX % spacing
        val offsetY = uiState.canvasOffsetY % spacing

        for (x in -1 until (width / spacing).toInt() + 2) {
            val startX = x * spacing + offsetX
            canvas.drawLine(startX, 0f, startX, height.toFloat(), gridPaint)
        }
        for (y in -1 until (height / spacing).toInt() + 2) {
            val startY = y * spacing + offsetY
            canvas.drawLine(0f, startY, width.toFloat(), startY, gridPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler?.handleTouchEvent(
            event,
            uiState.canvasScale,
            uiState.canvasOffsetX,
            uiState.canvasOffsetY
        ) ?: super.onTouchEvent(event)
    }
}
