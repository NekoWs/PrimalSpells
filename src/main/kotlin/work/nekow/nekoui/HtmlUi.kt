package work.nekow.nekoui

import net.minecraft.network.chat.Component
import work.nekow.nekoui.element.UiElement

/**
 * 一次 HTML 解析结果：窗口内容元件 + 窗口尺寸 + 窗口标题（div 的 title 属性）。
 */
class HtmlUi(
    val elements: List<UiElement>,
    val windowWidth: Int,
    val windowHeight: Int,
    val title: Component? = null,
)
