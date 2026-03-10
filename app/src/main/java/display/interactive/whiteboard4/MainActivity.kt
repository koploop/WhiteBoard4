package display.interactive.whiteboard4

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import display.interactive.accelerate.AccelerateCanvas
import display.interactive.whiteboard.element.BackgroundType
import display.interactive.whiteboard.element.ImageElement
import display.interactive.whiteboard.element.InteractionMode
import display.interactive.whiteboard.impl.WhiteBoardSDKImpl
import display.interactive.whiteboard.impl.WhiteBoardSurfaceView
import display.interactive.whiteboard4.view.MoreToolsPanelView
import display.interactive.whiteboard4.view.SettingsPanelView
import display.interactive.whiteboard4.view.ToolbarView
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var sdk: WhiteBoardSDKImpl
    private lateinit var whiteBoardView: WhiteBoardSurfaceView
    private lateinit var settingsPanel: SettingsPanelView
    private lateinit var toolbarView: ToolbarView
    private lateinit var moreToolsPanel: MoreToolsPanelView
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handleImagePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        sdk = ViewModelProvider(this).get(WhiteBoardSDKImpl::class.java)
        whiteBoardView = findViewById(R.id.whiteBoardView)
        whiteBoardView.setSDK(sdk)
        settingsPanel = findViewById(R.id.settingsPanel)
        toolbarView = findViewById(R.id.toolbarView)
        moreToolsPanel = findViewById(R.id.moreToolsPanel)

        // Initialize Accelerate Layer
        val metrics = resources.displayMetrics
        val size = Size(metrics.widthPixels, metrics.heightPixels)
        val accelerateCanvas = AccelerateCanvas(this, size)
        whiteBoardView.setAccelerateCanvas(accelerateCanvas)

        setupUI()
        observeState()
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupUI() {
        // Main Toolbar
        toolbarView.setOnDrawClickListener {
            sdk.setInteractionMode(InteractionMode.DRAW)
        }
        toolbarView.setOnZoomClickListener {
            val currentState = sdk.uiState.value
            if (!currentState.isZoomMode) {
                // Enable Zoom
                sdk.setZoomMode(true)
                sdk.setMultiFingerEnabled(false)

                if (currentState.isFingerSeparateMode) {
                    sdk.setFingerSeparateMode(false)
                    Toast.makeText(this, "已为您开启画布缩放, 多指书写已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "已为您开启画布缩放, 多指书写已关闭", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Disable Zoom
                sdk.setZoomMode(false)
                sdk.setMultiFingerEnabled(true)
                Toast.makeText(this, "已为您开启多指书写, 画布缩放已关闭", Toast.LENGTH_SHORT).show()
            }
        }

        toolbarView.setOnFingerSeparateClickListener {
            val currentState = sdk.uiState.value
            if (!currentState.isFingerSeparateMode) {
                // Enable Finger Separate
                sdk.setFingerSeparateMode(true)

                // If currently in Select or Eraser mode, switch to Draw mode
                if (currentState.interactionMode == InteractionMode.SELECT || 
                    currentState.interactionMode == InteractionMode.ERASER) {
                    sdk.setInteractionMode(InteractionMode.DRAW)
                }

                if (currentState.isZoomMode) {
                    sdk.setZoomMode(false)
                    sdk.setMultiFingerEnabled(true)
                    Toast.makeText(this, "手笔分离开启,已为您关闭画布缩放", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "手笔分离开启", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Disable Finger Separate
                sdk.setFingerSeparateMode(false)
            }
        }

        toolbarView.setOnSelectClickListener {
            sdk.setInteractionMode(InteractionMode.SELECT)
        }
        toolbarView.setOnEraserClickListener {
            sdk.setInteractionMode(InteractionMode.ERASER)
        }
        toolbarView.setOnUndoClickListener {
            sdk.undo()
        }
        toolbarView.setOnRedoClickListener {
            sdk.redo()
        }
        toolbarView.setOnClearClickListener {
            sdk.clear()
        }
        
        // Fix logic as per user request
        toolbarView.setOnMenuClickListener {
            settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        
        toolbarView.setOnSettingsClickListener {
            // User requested: btnSettings triggers pen settings
            Toast.makeText(this, "Pen Settings Triggered", Toast.LENGTH_SHORT).show()
        }

        toolbarView.setOnExitClickListener {
            finish()
        }

        toolbarView.setOnMoreClickListener {
            moreToolsPanel.show()
        }

        moreToolsPanel.setOnToolClickListener { item ->
            if (item.id == "image") {
                imagePickerLauncher.launch("image/*")
            } else {
                Toast.makeText(this, "该功能暂未实现", Toast.LENGTH_SHORT).show()
            }
        }

        // Settings Panel
        settingsPanel.setOnBgGridClickListener {
            sdk.setBackgroundType(BackgroundType.GRID)
        }
        settingsPanel.setOnBgSolidClickListener {
            sdk.setBackgroundType(BackgroundType.SOLID)
            sdk.setBackgroundColor(0xFFFFFFFF.toInt())
        }
        settingsPanel.setOnCopyClickListener {
            sdk.copySelectedElements()
        }
        settingsPanel.setOnPasteClickListener {
            sdk.pasteElements()
        }
        settingsPanel.setOnDeleteClickListener {
            sdk.deleteSelectedElements()
        }
        settingsPanel.setOnFrontClickListener {
            sdk.bringToFront()
        }
        settingsPanel.setOnBackClickListener {
            sdk.sendToBack()
        }
    }

    private fun handleImagePicked(uri: Uri?) {
        if (uri == null) {
            return
        }
        runCatching {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.onSuccess { bitmap ->
            addImageElementToBoard(scaleBitmapIfNeeded(bitmap))
            Toast.makeText(this, "图片已插入", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "图片插入失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = 2048f
        val sourceMax = maxOf(bitmap.width, bitmap.height).toFloat()
        if (sourceMax <= maxSide) {
            return bitmap
        }
        val ratio = maxSide / sourceMax
        val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun addImageElementToBoard(bitmap: Bitmap) {
        val state = sdk.uiState.value
        val viewWidth = whiteBoardView.width.toFloat().coerceAtLeast(1f)
        val viewHeight = whiteBoardView.height.toFloat().coerceAtLeast(1f)
        val worldCenterX = (viewWidth / 2f - state.canvasOffsetX) / state.canvasScale
        val worldCenterY = (viewHeight / 2f - state.canvasOffsetY) / state.canvasScale

        val maxWorldWidth = viewWidth * 0.5f / state.canvasScale
        val maxWorldHeight = viewHeight * 0.5f / state.canvasScale
        val widthRatio = maxWorldWidth / bitmap.width.toFloat()
        val heightRatio = maxWorldHeight / bitmap.height.toFloat()
        val displayRatio = minOf(widthRatio, heightRatio, 1f).coerceAtLeast(0.1f)
        val displayWidth = bitmap.width * displayRatio
        val displayHeight = bitmap.height * displayRatio

        val imageElement = ImageElement(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap,
            elementWidth = displayWidth,
            elementHeight = displayHeight,
            zIndex = state.elements.size
        )
        imageElement.x = worldCenterX
        imageElement.y = worldCenterY
        sdk.addElement(imageElement)
        sdk.selectElement(imageElement.id)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sdk.uiState.collect { state ->
                    whiteBoardView.updateState(state)
                    toolbarView.setZoomButtonState(state.isZoomMode)
                    toolbarView.setFingerSeparateButtonState(state.isFingerSeparateMode)
                    
                    // Update Select and Eraser buttons enabled state
                    val isFingerSeparate = state.isFingerSeparateMode
                    toolbarView.setSelectEnabled(!isFingerSeparate)
                    toolbarView.setEraserEnabled(!isFingerSeparate)
                }
            }
        }
    }
}
