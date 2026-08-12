package work.nekow.primalspells.ui

import work.nekow.primalspells.ui.element.UiElement

/**
 * 一次 HTML 解析结果：窗口内容元件 + 窗口尺寸。
 */
class HtmlUi(
    val elements: List<UiElement>,
    val windowWidth: Int,
    val windowHeight: Int,
)
