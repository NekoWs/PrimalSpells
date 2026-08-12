package work.nekow.nekoui

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import work.nekow.nekoui.element.UiButton
import work.nekow.nekoui.element.UiElement
import work.nekow.nekoui.element.UiLabel
import work.nekow.nekoui.element.UiSlot
import work.nekow.nekoui.element.UiWindow
import work.nekow.nekoui.html.HtmlUiParser

/**
 * UI 构建器：像 HTML 一样通过指定位置与大小来搭建界面。
 *
 * 用法：
 * ```
 * UiScreen.open(Component.literal("标题")) {
 *     centeredWindow(176, 166, Component.literal("窗口标题"))   // 居中窗口
 *     label(10, 20, Component.literal("一行文字"))
 *     button(10, 40, 80, 20, Component.literal("按钮")) { screen ->
 *         screen.close()
 *     }
 * }
 * ```
 */
class UiBuilder {
    val elements = mutableListOf<UiElement>()

    /** 窗口/面板：背景 + 边框 + 可选标题 */
    fun window(x: Int, y: Int, width: Int, height: Int, title: Component = Component.empty()): UiWindow {
        val window = UiWindow(x, y, width, height, title)
        elements += window
        return window
    }

    /** 居中的窗口（基于当前游戏窗口的缩放尺寸） */
    fun centeredWindow(width: Int, height: Int, title: Component = Component.empty()): UiWindow {
        val win = Minecraft.getInstance().window
        return window((win.guiScaledWidth - width) / 2, (win.guiScaledHeight - height) / 2, width, height, title)
    }

    /** 按钮：左键点击触发 [onClick]，回调以 `screen.() -> Unit` 接收者写法，可直接调用 close() */
    fun button(x: Int, y: Int, width: Int, height: Int, text: Component, onClick: UiScreen.() -> Unit) {
        elements += UiButton(x, y, width, height, text, onClick = { it.onClick() })
    }

    /** 文本标签 */
    fun label(x: Int, y: Int, text: Component, color: Int = 0xFF_E0E0E0.toInt()) {
        elements += UiLabel(text, x, y, color = color)
    }

    /** 物品槽位（默认 18×18），可指定物品与数量 */
    fun slot(x: Int, y: Int, stack: ItemStack = ItemStack.EMPTY, width: Int = 18, height: Int = 18) {
        elements += UiSlot(x, y, width, height, stack)
    }

    /** 解析 HTML 片段并映射为元件（[HtmlUiParser] 支持的子集） */
    fun html(source: String, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()) {
        elements += HtmlUiParser.parse(source, clicks)
    }
}
