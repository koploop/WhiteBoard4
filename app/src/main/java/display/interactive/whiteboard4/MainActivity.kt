package display.interactive.whiteboard4

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
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
import display.interactive.whiteboard.element.NoteElement
import display.interactive.whiteboard.element.NoteInteractionMode
import display.interactive.whiteboard.impl.NativeViewHostLayer
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
    private lateinit var nativeViewHostLayer: NativeViewHostLayer
    private var nativeViewFrameSyncEnabled = false
    private val nativeViewFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!nativeViewFrameSyncEnabled) return
            nativeViewHostLayer.sync(sdk.uiState.value)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
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
        val nativeViewHostContainer = findViewById<FrameLayout>(R.id.nativeViewHostContainer)
        nativeViewHostLayer = NativeViewHostLayer(this, nativeViewHostContainer)
        setupNativeViewHostLayer()

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

    override fun onStart() {
        super.onStart()
        startNativeViewFrameSync()
    }

    override fun onStop() {
        stopNativeViewFrameSync()
        super.onStop()
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
            when (item.id) {
                "image" -> imagePickerLauncher.launch("image/*")
                "note" -> {
                    addNoteElementToBoard()
                    Toast.makeText(this, "便签已插入", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "该功能暂未实现", Toast.LENGTH_SHORT).show()
                }
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

    private fun addNoteElementToBoard() {
        val state = sdk.uiState.value
        val viewWidth = whiteBoardView.width.toFloat().coerceAtLeast(1f)
        val viewHeight = whiteBoardView.height.toFloat().coerceAtLeast(1f)
        val worldCenterX = (viewWidth / 2f - state.canvasOffsetX) / state.canvasScale
        val worldCenterY = (viewHeight / 2f - state.canvasOffsetY) / state.canvasScale
        val noteWidth = 320f / state.canvasScale
        val noteHeight = 220f / state.canvasScale
        val noteElement = NoteElement(
            id = UUID.randomUUID().toString(),
            elementWidth = noteWidth,
            elementHeight = noteHeight,
            zIndex = state.elements.size
        )
        noteElement.x = worldCenterX - noteWidth / 2f
        noteElement.y = worldCenterY - noteHeight / 2f
        sdk.addElement(noteElement)
        sdk.selectElement(noteElement.id)
        sdk.setInteractionMode(InteractionMode.SELECT)
    }

    private fun setupNativeViewHostLayer() {
        nativeViewHostLayer.registerAdapter(object : NativeViewHostLayer.Adapter<NoteElement> {
            override val elementClass = NoteElement::class

            override fun createView(context: android.content.Context): View {
                val editText = EditText(context)
                editText.background = null
                editText.setTextColor(android.graphics.Color.BLACK)
                editText.setPadding(24, 24, 24, 24)
                editText.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                editText.isFocusable = false
                editText.isFocusableInTouchMode = false
                editText.isCursorVisible = false
                editText.setTextIsSelectable(false)
                editText.isVerticalScrollBarEnabled = true
                editText.isSingleLine = false
                editText.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                editText.setTag(R.id.tag_note_should_push_text, false)
                editText.setTag(R.id.tag_note_last_editing_state, false)
                editText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val shouldPush = editText.getTag(R.id.tag_note_should_push_text) as? Boolean ?: false
                        if (!shouldPush) return
                        sdk.updateActiveNoteText(s?.toString().orEmpty())
                    }
                })
                return editText
            }

            override fun bindView(view: View, element: NoteElement, state: display.interactive.whiteboard.element.WhiteBoardState) {
                val editText = view as EditText
                val width = (element.elementWidth * element.scaleX * state.canvasScale).toInt().coerceAtLeast(1)
                val height = (element.elementHeight * element.scaleY * state.canvasScale).toInt().coerceAtLeast(1)
                val left = element.x * state.canvasScale + state.canvasOffsetX
                val top = element.y * state.canvasScale + state.canvasOffsetY
                val params = editText.layoutParams as FrameLayout.LayoutParams
                params.width = width
                params.height = height
                editText.layoutParams = params
                editText.x = left
                editText.y = top
                editText.rotation = element.rotation
                editText.pivotX = 0f
                editText.pivotY = 0f
                editText.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                editText.setTextColor(element.textColor)
                val isEditing = state.activeNoteId == element.id && state.noteInteractionMode == NoteInteractionMode.TEXT_EDIT
                val wasEditing = editText.getTag(R.id.tag_note_last_editing_state) as? Boolean ?: false
                if (!isEditing && editText.text.toString() != element.text) {
                    editText.setTag(R.id.tag_note_should_push_text, false)
                    editText.setText(element.text)
                    editText.setSelection(editText.text.length)
                }
                editText.setTag(R.id.tag_note_should_push_text, isEditing)
                editText.isFocusable = isEditing
                editText.isFocusableInTouchMode = isEditing
                editText.isCursorVisible = isEditing
                editText.setTextIsSelectable(isEditing)
                editText.isEnabled = isEditing
                editText.isClickable = isEditing
                editText.isLongClickable = isEditing
                if (isEditing && !wasEditing) {
                    if (!editText.isFocused) {
                        editText.requestFocus()
                    }
                    val imm = getSystemService(InputMethodManager::class.java)
                    imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                } else if (!isEditing && wasEditing) {
                    if (editText.isFocused) {
                        editText.clearFocus()
                    }
                    val imm = getSystemService(InputMethodManager::class.java)
                    imm?.hideSoftInputFromWindow(editText.windowToken, 0)
                }
                editText.setTag(R.id.tag_note_last_editing_state, isEditing)
            }
        })
    }

    private fun startNativeViewFrameSync() {
        if (nativeViewFrameSyncEnabled) return
        nativeViewFrameSyncEnabled = true
        Choreographer.getInstance().postFrameCallback(nativeViewFrameCallback)
    }

    private fun stopNativeViewFrameSync() {
        nativeViewFrameSyncEnabled = false
        Choreographer.getInstance().removeFrameCallback(nativeViewFrameCallback)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sdk.uiState.collect { state ->
                    whiteBoardView.updateState(state)
                    nativeViewHostLayer.sync(state)
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
