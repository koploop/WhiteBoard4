package display.interactive.whiteboard4

import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ToggleButton
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
import display.interactive.whiteboard.element.InteractionMode
import display.interactive.whiteboard.impl.WhiteBoardSDKImpl
import display.interactive.whiteboard.impl.WhiteBoardSurfaceView
import kotlinx.coroutines.launch

import android.widget.Toast
import display.interactive.whiteboard4.view.SettingsPanelView
import display.interactive.whiteboard4.view.ToolbarView
import display.interactive.whiteboard4.view.MoreToolsPanelView

class MainActivity : AppCompatActivity() {

    private lateinit var sdk: WhiteBoardSDKImpl
    private lateinit var whiteBoardView: WhiteBoardSurfaceView
    private lateinit var settingsPanel: SettingsPanelView
    private lateinit var toolbarView: ToolbarView
    private lateinit var moreToolsPanel: MoreToolsPanelView

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

        // Settings Panel
        settingsPanel.setOnBgGridClickListener {
            sdk.setBackgroundType(BackgroundType.GRID)
        }
        settingsPanel.setOnBgSolidClickListener {
            sdk.setBackgroundType(BackgroundType.SOLID)
            sdk.setBackgroundColor(0xFFFFFFFF.toInt())
        }
        settingsPanel.setOnMultiFingerChangeListener { _, isChecked ->
            sdk.setMultiFingerEnabled(isChecked)
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

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sdk.uiState.collect { state ->
                    whiteBoardView.updateState(state)
                }
            }
        }
    }
}
