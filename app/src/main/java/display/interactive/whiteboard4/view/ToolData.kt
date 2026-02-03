package display.interactive.whiteboard4.view

import androidx.annotation.DrawableRes

data class ToolItem(
    val id: String,
    val name: String,
    @DrawableRes val iconRes: Int,
    val action: () -> Unit = {}
)

data class ToolColumn(
    val items: List<ToolItem>
)

object ToolConfig {
    fun getMoreToolsData(): List<ToolColumn> {
        return listOf(
            ToolColumn(listOf(
                ToolItem("image", "插入图片", display.interactive.whiteboard4.R.drawable.ic_tool_image),
                ToolItem("video", "打开视频", display.interactive.whiteboard4.R.drawable.ic_tool_video),
                ToolItem("doc", "打开文档", display.interactive.whiteboard4.R.drawable.ic_tool_doc),
                ToolItem("resource", "资源库", display.interactive.whiteboard4.R.drawable.ic_tool_resource),
                ToolItem("browser", "浏览器", display.interactive.whiteboard4.R.drawable.ic_tool_browser)
            )),
            ToolColumn(listOf(
                ToolItem("shapes", "图形", display.interactive.whiteboard4.R.drawable.ic_tool_shapes),
                ToolItem("ruler", "尺规工具", display.interactive.whiteboard4.R.drawable.ic_tool_ruler),
                ToolItem("function", "函数", display.interactive.whiteboard4.R.drawable.ic_tool_function),
                ToolItem("mindmap", "思维导图", display.interactive.whiteboard4.R.drawable.ic_tool_mindmap),
                ToolItem("table", "表格", display.interactive.whiteboard4.R.drawable.ic_tool_table)
            )),
            ToolColumn(listOf(
                ToolItem("flowchart", "流程图", display.interactive.whiteboard4.R.drawable.ic_tool_flowchart),
                ToolItem("tactics", "战术板", display.interactive.whiteboard4.R.drawable.ic_tool_tactics),
                ToolItem("note", "便签", display.interactive.whiteboard4.R.drawable.ic_tool_note)
            )),
            ToolColumn(listOf(
                ToolItem("stopwatch", "秒表", display.interactive.whiteboard4.R.drawable.ic_tool_stopwatch),
                ToolItem("countdown", "倒计时", display.interactive.whiteboard4.R.drawable.ic_tool_countdown),
                ToolItem("poll", "投票器", display.interactive.whiteboard4.R.drawable.ic_tool_poll),
                ToolItem("calculator", "计算器", display.interactive.whiteboard4.R.drawable.ic_tool_calculator)
            ))
        )
    }
}
