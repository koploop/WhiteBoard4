package display.interactive.whiteboard4.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.content.res.ColorStateList
import androidx.constraintlayout.widget.ConstraintLayout
import display.interactive.whiteboard4.R

class ToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.view_toolbar, this, true)
    }

    fun setOnExitClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnExit).setOnClickListener(listener)
    }

    fun setOnMenuClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnMenu).setOnClickListener(listener)
    }

    fun setOnSaveClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnSave).setOnClickListener(listener)
    }

    fun setOnShareClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnShare).setOnClickListener(listener)
    }

    fun setOnDrawClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnDraw).setOnClickListener(listener)
    }

    fun setOnZoomClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnZoom).setOnClickListener(listener)
    }

    fun setOnFingerSeparateClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnFingerSeparate).setOnClickListener(listener)
    }

    fun setZoomButtonState(isActive: Boolean) {
        val color = if (isActive) context.getColor(R.color.white) else context.getColor(R.color.disable_gray)
        findViewById<ImageButton>(R.id.btnZoom).backgroundTintList = ColorStateList.valueOf(color)
    }

    fun setFingerSeparateButtonState(isActive: Boolean) {
        val color = if (isActive) context.getColor(R.color.white) else context.getColor(R.color.disable_gray)
        findViewById<ImageButton>(R.id.btnFingerSeparate).backgroundTintList = ColorStateList.valueOf(color)
    }

    fun setOnSettingsClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnSettings).setOnClickListener(listener)
    }

    fun setOnEraserClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnEraser).setOnClickListener(listener)
    }

    fun setOnSelectClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnSelect).setOnClickListener(listener)
    }

    fun setOnMoreClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnMore).setOnClickListener(listener)
    }

    fun setOnUndoClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnUndo).setOnClickListener(listener)
    }

    fun setOnRedoClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnRedo).setOnClickListener(listener)
    }

    fun setOnClearClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnClear).setOnClickListener(listener)
    }

    fun setOnAddPageClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnAddPage).setOnClickListener(listener)
    }

    fun setOnPrevPageClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnPrevPage).setOnClickListener(listener)
    }

    fun setOnNextPageClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnNextPage).setOnClickListener(listener)
    }

    fun setOnMapClickListener(listener: (View) -> Unit) {
        findViewById<View>(R.id.btnMap).setOnClickListener(listener)
    }

    fun setEraserEnabled(enabled: Boolean) {
        val btn = findViewById<ImageButton>(R.id.btnEraser)
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1.0f else 0.5f
    }

    fun setSelectEnabled(enabled: Boolean) {
        val btn = findViewById<ImageButton>(R.id.btnSelect)
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1.0f else 0.5f
    }
}
