package display.interactive.whiteboard4.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ToggleButton
import display.interactive.whiteboard4.R

class SettingsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_settings_panel, this, true)
    }

    fun setOnBgGridClickListener(listener: (View) -> Unit) {
        findViewById<Button>(R.id.btnBgGrid).setOnClickListener(listener)
    }

    fun setOnBgSolidClickListener(listener: (View) -> Unit) {
        findViewById<Button>(R.id.btnBgSolid).setOnClickListener(listener)
    }

    fun setOnMultiFingerChangeListener(listener: (View, Boolean) -> Unit) {
        findViewById<ToggleButton>(R.id.toggleMultiFinger).setOnCheckedChangeListener { view, isChecked ->
            listener(view, isChecked)
        }
    }

    fun setOnCopyClickListener(listener: (View) -> Unit) {
        findViewById<Button>(R.id.btnCopy).setOnClickListener(listener)
    }

    fun setOnPasteClickListener(listener: (View) -> Unit) {
        findViewById<Button>(R.id.btnPaste).setOnClickListener(listener)
    }

    fun setOnDeleteClickListener(listener: (View) -> Unit) {
        findViewById<Button>(R.id.btnDelete).setOnClickListener(listener)
    }

    fun setOnFrontClickListener(listener: (View) -> Unit) {
        findViewById<Button>(R.id.btnFront).setOnClickListener(listener)
    }

    fun setOnBackClickListener(listener: (View) -> Unit) {
        findViewById<Button>(R.id.btnBack).setOnClickListener(listener)
    }
}
