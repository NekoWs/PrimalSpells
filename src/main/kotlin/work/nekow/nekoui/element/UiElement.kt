package work.nekow.nekoui.element

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import work.nekow.nekoui.UiScreen
import work.nekow.nekoui.html.CssRule
import work.nekow.nekoui.html.Style

/**
 * UI 元素基类。所有元件都通过 (x, y, width, height) 定位，类似 HTML 的绝对定位。
 * [screen] 在无宿主屏幕渲染时（如悬浮窗口）为 null。
 */
abstract class UiElement(
    val x: Int,
    val y: Int,
    var width: Int,
    var height: Int,
) {
    var visible: Boolean = true

    /** HTML id 标识（从 html 的 id 属性解析，供代码按 id 查找/更新元素） */
    open val id: String? = null

    /** CSS 类名列表（对应 `class` 属性，可用 [addClass]/[removeClass]/[toggleClass] 运行期修改） */
    val classList = mutableListOf<String>()

    /** 可选匹配的 CSS 规则（由 HtmlUiParser 注入全部规则，渲染时按当前 classList 动态匹配） */
    internal var cssRules: List<CssRule> = emptyList()

    fun addClass(name: String): UiElement {
        if (name !in classList) classList += name
        return this
    }

    fun removeClass(name: String): UiElement {
        classList -= name
        return this
    }

    /** 切换类；[force] 为 true 强制添加、false 强制移除、null 自动切换。@return 操作后的添加状态 */
    fun toggleClass(name: String, force: Boolean? = null): Boolean {
        val add = force ?: (name !in classList)
        if (add) addClass(name) else removeClass(name)
        return add
    }

    fun hasClass(name: String): Boolean = name in classList

    /** 判断坐标是否落在元件范围内 */
    fun contains(mx: Double, my: Double): Boolean =
        mx >= x && mx < x + width && my >= y && my < y + height

    /**
     * 解析 CSS 颜色属性（含 :hover 状态切换）：
     * 匹配规则的属性值优先，其次按悬停状态取 [hovered]（未悬停取 [normal]）。
     *
     * @param prop CSS 属性名（如 color / background / border-color）
     */
    fun cssColorState(prop: String, normal: Int, hovered: Int, mouseX: Int, mouseY: Int): Int {
        val h = contains(mouseX.toDouble(), mouseY.toDouble())
        val map = if (h) cssMap(true) else cssMap(false)
        val v = map[prop] ?: return if (h) hovered else normal
        return Style.parseColor(v)
    }

    /** 当前生效的 CSS 属性表（按 classList 匹配 + 悬停状态过滤，后声明覆盖先声明） */
    private fun cssMap(hovered: Boolean): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        for (rule in cssRules) {
            if (rule.hover != hovered || !rule.matches(classList)) continue
            merged.putAll(rule.declarations)
        }
        return merged
    }

    /** 获取可用字体（悬浮窗口等无宿主场景回退到 Minecraft 主字体） */
    protected fun fontOf(screen: UiScreen?): Font =
        screen?.font ?: Minecraft.getInstance().font

    open fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}

    /** @return true 表示消费了这次点击 */
    open fun mouseClicked(screen: UiScreen?, button: Int, mx: Double, my: Double): Boolean = false
}
