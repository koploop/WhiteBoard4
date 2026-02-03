package display.interactive.whiteboard4.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import display.interactive.whiteboard4.R

class MoreToolsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.view_more_tools, this, true)
        visibility = GONE
        
        findViewById<View>(R.id.rootContainer).setOnClickListener {
            hide()
        }

        findViewById<View>(R.id.cardPanel).setOnClickListener {
            // Do nothing, prevent closing when clicking inside the panel
        }
        
        setupColumns()
    }

    private fun setupColumns() {
        val columnContainer = findViewById<LinearLayout>(R.id.columnContainer)
        val data = ToolConfig.getMoreToolsData()
        
        data.forEachIndexed { index, column ->
            val rv = RecyclerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutManager = LinearLayoutManager(context)
                adapter = ToolAdapter(column.items)
                overScrollMode = View.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = false // Disable nested scrolling to allow wrap_content to work correctly in some containers
            }
            columnContainer.addView(rv)
            
            if (index < data.size - 1) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                        setMargins(12, 0, 12, 0)
                    }
                    setBackgroundColor(0x22FFFFFF)
                }
                columnContainer.addView(divider)
            }
        }
    }

    fun show() {
        visibility = VISIBLE
    }

    fun hide() {
        visibility = GONE
    }

    private class ToolAdapter(private val items: List<ToolItem>) : 
        RecyclerView.Adapter<ToolAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
            val tvName: TextView = view.findViewById(R.id.tvName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.ivIcon.setImageResource(item.iconRes)
            holder.tvName.text = item.name
            holder.itemView.setOnClickListener {
                item.action()
            }
        }

        override fun getItemCount() = items.size
    }
}
