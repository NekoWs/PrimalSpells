package work.nekow.primalspells.ui.html

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.nekow.primalspells.ui.HtmlUi
import work.nekow.primalspells.ui.UiScreen
import work.nekow.primalspells.ui.common.SpellGridPlaceholder
import work.nekow.primalspells.ui.element.TextAlign
import work.nekow.primalspells.ui.element.UiButton
import work.nekow.primalspells.ui.element.UiDivider
import work.nekow.primalspells.ui.element.UiElement
import work.nekow.primalspells.ui.element.UiImage
import work.nekow.primalspells.ui.element.UiLabel
import work.nekow.primalspells.ui.element.UiProgressBar
import work.nekow.primalspells.ui.element.UiSlot
import work.nekow.primalspells.ui.element.UiToggle
import work.nekow.primalspells.ui.element.UiWindow

/**
 * 极简 HTML → MC UI 映射器。
 *
 * 支持标签：
 * - `div`       → [UiWindow] 面板（子元素相对面板定位；`display:flex` 自动布局）
 * - `button` / `a` → [UiButton] 按钮（onclick="close" 关闭，或自定义回调名）
 * - `label`/`span`/`p`/`h1`~`h3` → [UiLabel] 文本（h1/h2/h3 自动放大字号）
 * - `img`       → [UiImage] 图片（src 为资源包贴图路径，如 `primalspells:textures/item/wand.png`）
 * - `slot`      → [UiSlot] 物品槽位（src 为物品注册名如 `primalspells:wand`，count 为数量，留空为空白槽位）
 * - `spell-grid`→ [SpellGridPlaceholder] 动态法术槽网格占位（left/top 为网格起点，per-row 为每行槽位数），
 *   由容器窗口（法杖信息窗/小包窗）按运行时数据替换为实际槽位
 * - `progress`  → [UiProgressBar] 进度条（value/max 属性；id 供运行时更新 value）
 * - `checkbox`  → [UiToggle] 开关（checked 属性；onclick 可选）
 * - `hr`        → [UiDivider] 水平分隔线
 * - `br`        → 文本换行
 *
 * 支持 style 属性：
 * - `left` / `top` / `width` / `height`：像素（可省略 px）、百分比（相对父元素；顶层相对屏幕）或
 *   `auto`（div 上按子元素边界自动计算，窗口根 div 支持自动尺寸）
 * - `color` / `background` / `border-color` / `hover-background` / `hover-color`：颜色
 *   （#RGB、#RRGGBB、#RRGGBBAA、rgb()/rgba()，或命名颜色：white/black/gray/red/green/blue/yellow/
 *   orange/purple/cyan/transparent）
 * - `text-align`: left / center / right
 * - `font-size`：字号（像素，相对默认 9px，如 18 = 2 倍）
 * - `background-image`：url(...) 或直接贴图路径（div 背景图）
 * - `title`：面板标题文字（窗口根 div 的 title 作为窗口顶栏标题）
 * - `display:flex` + `flex-direction:row/column` + `gap` + `align-items:center`：自动布局子元素
 * - `padding`：div 内容区内边距（flex 与 auto 布局生效）
 * - `hidden` 属性：元素不渲染
 * - `enabled="false"`：按钮禁用
 *
 * 所有元素支持 `id` 属性：代码可通过 `element.id` 查找并动态更新。
 *
 * 示例：
 * ```
 * <div style="left:20%;top:15%;width:auto;height:auto;background:#1a1a1e;border-color:#4a4a55"
 *      title="法杖编辑台">
 *     <div style="display:flex;flex-direction:column;gap:4;padding:6">
 *         <label style="text-align:center;color:white;font-size:18">法术编辑</label>
 *         <hr/>
 *         <slot src="primalspells:fireball"/>
 *         <progress value="75" max="100" style="color:#33ccff;background:#22222a"/>
 *         <checkbox checked="true">自动施放</checkbox>
 *         <button style="width:70;height:20" onclick="close">关闭</button>
 *     </div>
 * </div>
 * ```
 */
object HtmlUiParser {

    fun parse(html: String, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()): List<UiElement> {
        val roots = HtmlParser(html).parse()
        val win = Minecraft.getInstance().window
        val elements = mutableListOf<UiElement>()
        roots.forEach { convert(it, 0, 0, win.guiScaledWidth, win.guiScaledHeight, clicks, elements) }
        return elements
    }

    /**
     * 解析为窗口形式：第一个顶层 `div` 作为窗口本体，
     * 其 `width`/`height`（像素或百分比）即窗口尺寸，`left`/`top` 忽略（窗口自动居中），
     * 也支持 `width:auto`/`height:auto`（按子元素边界自动计算）。
     * `title` 属性作为窗口标题（顶栏，见 [HtmlUi.title]），不再生成正文区面板标题。
     * 子元素以窗口内容区左上角为原点定位。
     */
    fun parseWindowed(html: String, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()): HtmlUi {
        val roots = HtmlParser(html).parse()
        val win = Minecraft.getInstance().window
        val screenW = win.guiScaledWidth
        val screenH = win.guiScaledHeight
        val elements = mutableListOf<UiElement>()

        val windowNode = roots.firstNotNullOfOrNull { findFirstDiv(it) }
        if (windowNode == null) {
            roots.forEach { convert(it, 0, 0, screenW, screenH, clicks, elements) }
            return HtmlUi(elements, screenW, screenH)
        }

        val style = Style.parse(windowNode.attrs["style"])
        val w0 = style.dim("width", screenW)
        val h0 = style.dim("height", screenH)
        convert(windowNode, 0, 0, w0 ?: screenW, h0 ?: screenH, clicks, elements, windowRoot = true)
        // auto 尺寸：取转换后窗口面板的实际尺寸
        val rootWindow = elements.filterIsInstance<UiWindow>().firstOrNull()
        return HtmlUi(
            elements,
            w0 ?: rootWindow?.width ?: screenW,
            h0 ?: rootWindow?.height ?: screenH,
            title = windowNode.attrs["title"]?.let { Component.literal(it) },
        )
    }

    /**
     * 从资源包读取 HTML 文件（基于 Identifier，如 `primalspells:html/index.html`）。
     * 文件应位于 `assets/<namespace>/html/...`，F3+T 重载后可取到最新内容。
     */
    fun load(identifier: Identifier): String {
        val resource = Minecraft.getInstance().getResourceManager().getResource(identifier)
            .orElseThrow { IllegalStateException("HTML resource not found: $identifier") }
        return resource.open().bufferedReader().use { it.readText() }
    }

    /** 读取并解析一个 HTML 资源文件 */
    fun loadAndParse(identifier: Identifier, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()): List<UiElement> =
        parse(load(identifier), clicks)

    private fun convert(
        node: Node,
        parentX: Int,
        parentY: Int,
        parentW: Int,
        parentH: Int,
        clicks: Map<String, UiScreen.() -> Unit>,
        out: MutableList<UiElement>,
        windowRoot: Boolean = false,
        flexX: Int? = null,
        flexY: Int? = null,
        flexW: Int? = null,
        flexH: Int? = null,
    ) {
        val style = Style.parse(node.attrs["style"])
        val x = flexX ?: (parentX + (style.dim("left", parentW) ?: 0))
        val y = flexY ?: (parentY + (style.dim("top", parentH) ?: 0))
        val w = flexW ?: (style.dim("width", parentW) ?: 0)
        val h = flexH ?: (style.dim("height", parentH) ?: 0)
        val text = node.text()
        val align = style.align()
        val id = node.attrs["id"]

        when (node.tag) {
            "br" -> Unit

            // 文档级标签：仅递归子元素，不生成 UI（兼容完整 HTML 文档：html/head/body/meta/title/style 等）
            "html", "head", "body", "meta", "link", "style", "script", "title" -> {
                node.children.forEach { convert(it, parentX, parentY, parentW, parentH, clicks, out) }
            }

            "div" -> {
                val flexDir = style.flexDirection()
                val childOut = mutableListOf<UiElement>()
                val autoFlexW = if (flexDir != null) {
                    // flex 自动布局：子元素沿主轴依次排布，返回 auto 宽度估算
                    renderFlexChildren(node, x, y, parentW, parentH, style, flexDir, clicks, childOut)
                } else {
                    node.children.forEach { convert(it, x, y, parentW, parentH, clicks, childOut) }
                    0
                }
                // 动态大小：width/height:auto 时按子元素边界计算（面板先加入 out 保证渲染在底层）
                val aw = when {
                    style.isAuto("width") && flexDir != null -> autoFlexW
                    style.isAuto("width") -> (childOut.maxOfOrNull { it.x + it.width } ?: 0) - x
                    else -> w
                }
                val ah = if (style.isAuto("height")) (childOut.maxOfOrNull { it.y + it.height } ?: 0) - y else h
                out += applyAttrs(UiWindow(
                    x, y, aw, ah,
                    // 窗口根 div 的 title 只作为窗口标题（顶栏），不生成正文区面板标题
                    title = if (windowRoot) Component.empty()
                    else node.attrs["title"]?.let { Component.literal(it) } ?: Component.empty(),
                    background = style.colorOf("background") ?: 0xF0_1A_1A_1E.toInt(),
                    borderColor = style.colorOf("border-color") ?: 0xFF_3D_3D_42.toInt(),
                    image = style.identifier("background-image"),
                    id = id,
                ), node)
                out += childOut
            }

            "img" -> {
                val src = node.attrs["src"]?.let { parseIdentifier(it) } ?: return
                out += applyAttrs(UiImage(x, y, w, h, src, id = id), node)
            }

            "slot" -> {
                val stack = parseItemStack(node.attrs["src"], node.attrs["count"])
                out += applyAttrs(UiSlot(
                    x, y,
                    width = if (w > 0) w else 18,
                    height = if (h > 0) h else 18,
                    stack = stack,
                    id = id,
                ), node)
            }

            "spell-grid" -> {
                out += applyAttrs(SpellGridPlaceholder(
                    id, x, y,
                    perRow = node.attrs["per-row"]?.toIntOrNull() ?: 8,
                    width = w,
                    height = h,
                ), node)
            }

            "button", "a" -> {
                val actionName = node.attrs["onclick"] ?: return
                val action: UiScreen.() -> Unit = when {
                    actionName == "close" -> { s: UiScreen -> s.close() }
                    clicks.containsKey(actionName) -> clicks.getValue(actionName)
                    else -> { s: UiScreen -> s.close() }
                }
                out += applyAttrs(UiButton(
                    x, y, w, h,
                    Component.literal(text),
                    action,
                    enabled = node.attrs["enabled"] != "false",
                    background = style.colorOf("background") ?: 0xFF_2B_2B_30.toInt(),
                    hoverBackground = style.colorOf("hover-background") ?: 0xFF_40_40_46.toInt(),
                    borderColor = style.colorOf("border-color") ?: 0xFF_4E_4E_55.toInt(),
                    textColor = style.colorOf("color") ?: 0xFF_C8_C8_C8.toInt(),
                    hoverTextColor = style.colorOf("hover-color") ?: 0xFF_FF_FF_FF.toInt(),
                    align = align,
                    fontScale = style.fontScale() ?: 1f,
                    id = id,
                ), node)
            }

            "label", "span", "p", "h1", "h2", "h3" -> {
                // 标题级字号：h1=2x, h2=1.5x, h3=1.2x（可用 font-size 覆盖）
                val defaultScale = when (node.tag) {
                    "h1" -> 2f
                    "h2" -> 1.5f
                    "h3" -> 1.2f
                    else -> 1f
                }
                out += applyAttrs(UiLabel(
                    Component.literal(text),
                    x, y, w, h,
                    color = style.colorOf("color") ?: 0xFF_E0E0E0.toInt(),
                    align = align,
                    fontScale = style.fontScale() ?: defaultScale,
                    id = id,
                ), node)
            }

            "hr" -> {
                out += applyAttrs(UiDivider(
                    x, y,
                    width = if (w > 0) w else (parentX + parentW - x).coerceAtLeast(0),
                    height = if (h > 0) h else 1,
                    color = style.colorOf("color") ?: style.colorOf("border-color") ?: 0xFF_4A_4A_55.toInt(),
                    id = id,
                ), node)
            }

            "progress" -> {
                out += applyAttrs(UiProgressBar(
                    x, y,
                    width = if (w > 0) w else parentW,
                    height = if (h > 0) h else 4,
                    value = node.attrs["value"]?.toFloatOrNull() ?: 0f,
                    max = node.attrs["max"]?.toFloatOrNull() ?: 100f,
                    color = style.colorOf("color") ?: 0xFF_33_99_FF.toInt(),
                    backgroundColor = style.colorOf("background") ?: 0xFF_2A_2A_32.toInt(),
                    id = id,
                ), node)
            }

            "checkbox" -> {
                val actionName = node.attrs["onclick"]
                out += applyAttrs(UiToggle(
                    x, y,
                    width = if (w > 0) w else fontWidthOf(text) + 16,
                    height = if (h > 0) h else 12,
                    Component.literal(text),
                    node.attrs["checked"] == "true",
                    onClick = { screen, checked ->
                        if (actionName != null && actionName != "close" && clicks.containsKey(actionName)) {
                            screen?.let { clicks.getValue(actionName).invoke(it) }
                        }
                    },
                    id = id,
                ), node)
            }
        }
    }

    /** 递归查找第一个 div 节点（兼容完整 HTML 文档：div 可能位于 html/body 内） */
    private fun findFirstDiv(node: Node): Node? {
        if (node.tag == "div") return node
        node.children.forEach { findFirstDiv(it)?.let { return it } }
        return null
    }

    /**
     * flex 自动布局子元素（row / column + gap + align-items:center）。
     * 未声明 left/top 的子元素沿主轴依次排布，交叉轴按 align-items 对齐；
     * 已声明 left/top 的子元素保持绝对定位。
     *
     * @return 容器宽度为 auto 时的估算宽度（声明宽度或按子元素自然宽度推算，供占满元素与 auto 计算使用）
     */
    private fun renderFlexChildren(
        node: Node,
        x: Int,
        y: Int,
        parentW: Int,
        parentH: Int,
        containerStyle: Style,
        flexDir: String,
        clicks: Map<String, UiScreen.() -> Unit>,
        out: MutableList<UiElement>,
    ): Int {
        val padding = containerStyle.dim("padding", parentW) ?: 0
        val gap = containerStyle.dim("gap", parentW) ?: 2
        val alignCenter = containerStyle.alignItemsCenter()

        // 容器有效宽度：声明值，或按子元素自然宽度估算（供占满元素与 auto 计算使用）
        val declaredW = containerStyle.dim("width", parentW)
        val naturalW = node.children.mapNotNull { child ->
            val cs = Style.parse(child.attrs["style"])
            if (cs.dim("width", parentW) != null) null else flexChildWidth(child).takeIf { it > 0 }
        }.maxOrNull() ?: 0
        val containerW = declaredW ?: (naturalW + padding * 2).coerceAtLeast(0)
        val crossW = (containerW - padding * 2).coerceAtLeast(0)

        // 容器有效高度（row 方向交叉轴对齐用）
        val declaredH = containerStyle.dim("height", parentH)
        val naturalH = node.children.mapNotNull { child ->
            val cs = Style.parse(child.attrs["style"])
            if (cs.dim("height", parentH) != null) null else flexChildHeight(child)
        }.maxOrNull() ?: 0
        val containerH = declaredH ?: (naturalH + padding * 2).coerceAtLeast(0)
        val crossH = (containerH - padding * 2).coerceAtLeast(0)

        var cursorX = padding
        var cursorY = padding

        for (child in node.children) {
            val cs = Style.parse(child.attrs["style"])
            val declaredL = cs.dim("left", parentW)
            val declaredT = cs.dim("top", parentH)
            val auto = declaredL == null && declaredT == null
            val declaredWc = cs.dim("width", parentW)
            val declaredHc = cs.dim("height", parentH)
            val cw = declaredWc ?: flexChildWidth(child).takeIf { it > 0 } ?: if (flexDir == "column") crossW else 18
            val ch = declaredHc ?: flexChildHeight(child)

            val ax = when {
                !auto -> null
                flexDir == "row" -> cursorX
                else -> if (alignCenter) padding + (crossW - cw) / 2 else padding
            }
            val ay = when {
                !auto -> null
                flexDir == "column" -> cursorY
                else -> if (alignCenter) padding + (crossH - ch) / 2 else padding
            }

            convert(
                child, x, y, parentW, parentH, clicks, out,
                flexX = ax?.let { x + it },
                flexY = ay?.let { y + it },
                flexW = if (declaredWc == null) cw else null,
                flexH = if (declaredHc == null) ch else null,
            )

            if (flexDir == "column") cursorY += ch + gap else cursorX += cw + gap
        }

        // row 方向：auto 宽度取实际排布总长（子元素声明宽度时也精确）
        if (flexDir == "row" && declaredW == null) return cursorX + padding
        return containerW
    }

    /** flex 子元素未声明宽度时的默认宽度 */
    private fun flexChildWidth(child: Node): Int = when (child.tag) {
        "label", "span", "p", "h1", "h2", "h3" -> fontWidthOf(child.text())
        "checkbox" -> fontWidthOf(child.text()) + 16
        "slot" -> 18
        "button", "a" -> 60
        else -> 0
    }

    /** flex 子元素未声明高度时的默认高度 */
    private fun flexChildHeight(child: Node): Int = when (child.tag) {
        "hr" -> 1
        "progress" -> 4
        "slot" -> 18
        "checkbox" -> 12
        "button", "a" -> 20
        "label", "span", "p", "h1", "h2", "h3" -> 9
        else -> 18
    }

    private fun fontWidthOf(text: String): Int = Minecraft.getInstance().font.width(text)

    /** 统一应用通用属性（如 hidden） */
    private fun <T : UiElement> applyAttrs(el: T, node: Node): T {
        if (node.attrs.containsKey("hidden")) el.visible = false
        return el
    }
}

/** 极简 DOM 节点 */
class Node(
    val tag: String,
    val attrs: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<Node> = mutableListOf(),
) {
    private val textContent = StringBuilder()
    fun appendText(t: String) { textContent.append(t) }
    fun text(): String = textContent.toString().trim()
}

/** 极简 HTML 解析器：栈式扫描，只处理开/闭/自闭合标签 */
class HtmlParser(private val html: String) {

    fun parse(): List<Node> {
        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        var i = 0
        while (i < html.length) {
            val lt = html.indexOf('<', i)
            if (lt == -1) {
                stack.lastOrNull()?.appendText(html.substring(i))
                break
            }
            stack.lastOrNull()?.appendText(html.substring(i, lt))
            val gt = html.indexOf('>', lt)
            if (gt == -1) break
            val content = html.substring(lt + 1, gt).trim()
            i = gt + 1

            when {
                content.startsWith("!") || content.startsWith("?") -> Unit

                content.startsWith("/") -> {
                    val name = content.substring(1).trim()
                    if (stack.isNotEmpty() && stack.last().tag == name) {
                        val closed = stack.removeLast()
                        if (stack.isEmpty()) roots += closed else stack.last().children += closed
                    }
                }

                content.endsWith("/") -> {
                    val (name, attrs) = splitTag(content.dropLast(1))
                    val node = Node(name, attrs)
                    if (stack.isEmpty()) roots += node else stack.last().children += node
                }

                else -> {
                    val (name, attrs) = splitTag(content)
                    if (name.isNotEmpty()) stack.addLast(Node(name, attrs))
                }
            }
        }
        stack.forEach { roots += it }
        return roots
    }

    private fun splitTag(content: String): Pair<String, MutableMap<String, String>> {
        val parts = content.split(Regex("\\s+"), limit = 2)
        val name = parts[0]
        val attrs = mutableMapOf<String, String>()
        if (parts.size == 2) {
            Regex("""(\w[\w-]*)="([^"]*)"""").findAll(parts[1]).forEach { m ->
                attrs[m.groupValues[1]] = m.groupValues[2]
            }
        }
        return name to attrs
    }
}

/** 极简 CSS 样式解析 */
class Style private constructor(private val values: Map<String, String>) {

    /** 读取尺寸：像素或百分比（相对 parent） */
    fun dim(key: String, parent: Int): Int? {
        val v = values[key]?.trim() ?: return null
        return if (v.endsWith("%")) {
            (parent * v.dropLast(1).trim().toDoubleOrNull()!! / 100.0).toInt()
        } else {
            v.trimEnd('p', 'x').toIntOrNull()
        }
    }

    fun colorOf(key: String): Int? = values[key]?.let { parseColor(it) }

    /** 读取资源路径（支持 `url(...)` 包裹），如 `url(primalspells:textures/item/wand.png)` */
    fun identifier(key: String): Identifier? = values[key]?.let { parseIdentifier(it) }

    fun align(): TextAlign = when (values["text-align"]?.trim()) {
        "center" -> TextAlign.CENTER
        "right" -> TextAlign.RIGHT
        else -> TextAlign.LEFT
    }

    /** 尺寸是否为 `auto`（按子元素边界自动计算） */
    fun isAuto(key: String): Boolean = values[key]?.trim() == "auto"

    /** `display:flex` 时的主轴方向（row / column），非 flex 返回 null */
    fun flexDirection(): String? {
        if (values["display"]?.trim() != "flex") return null
        val dir = values["flex-direction"]?.trim()
        return if (dir == "row" || dir == "column") dir else "column"
    }

    /** 交叉轴是否居中（`align-items:center`） */
    fun alignItemsCenter(): Boolean = values["align-items"]?.trim() == "center"

    /** 字号倍率（`font-size` 像素 / 9），未声明返回 null */
    fun fontScale(): Float? = dim("font-size", 0)?.takeIf { it > 0 }?.div(9f)

    companion object {
        fun parse(style: String?): Style {
            val map = mutableMapOf<String, String>()
            style?.split(';')?.forEach { kv ->
                val parts = kv.split(':', limit = 2)
                if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
            }
            return Style(map)
        }

        private val NAMED = mapOf(
            "white" to 0xFFFFFF, "black" to 0x000000, "gray" to 0x808080,
            "lightgray" to 0xC0C0C0, "darkgray" to 0x404040,
            "red" to 0xFF5555, "green" to 0x55FF55, "blue" to 0x5555FF,
            "yellow" to 0xFFFF55, "orange" to 0xFFAA00, "purple" to 0xAA55FF,
            "cyan" to 0x55FFFF,
        )

        fun parseColor(s: String): Int {
            val t = s.trim().lowercase()
            return when {
                t.startsWith("rgb") -> parseRgb(t)
                t.startsWith("#") && t.length == 9 -> {
                    // #RRGGBBAA（8 位十六进制）
                    val v = t.substring(1).toLong(16).toInt()
                    (v and 0xFFFFFF) or ((v ushr 24) shl 24)
                }
                t.startsWith("#") && t.length == 7 ->
                    (0xFF shl 24) or (t.substring(1).toLong(16).toInt() and 0xFFFFFF)
                t.startsWith("#") && t.length == 4 -> {
                    val r = t[1].digitToInt(16); val g = t[2].digitToInt(16); val b = t[3].digitToInt(16)
                    (0xFF shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
                }
                t == "transparent" -> 0x00000000
                NAMED.containsKey(t) -> (0xFF shl 24) or NAMED.getValue(t)
                else -> 0xFF_E0E0E0.toInt()
            }
        }

        /** 解析 `rgb(r,g,b)` / `rgba(r,g,b,a)`，a 支持 0..1、0..255、百分比 */
        private fun parseRgb(t: String): Int {
            val inner = t.substringAfter('(').substringBefore(')')
            val parts = inner.split(',').map { it.trim() }
            if (parts.size < 3) return 0xFF_E0E0E0.toInt()
            val r = parts[0].toIntOrNull()?.coerceIn(0, 255) ?: 0
            val g = parts[1].toIntOrNull()?.coerceIn(0, 255) ?: 0
            val b = parts[2].toIntOrNull()?.coerceIn(0, 255) ?: 0
            var a = 1f
            parts.getOrNull(3)?.let { raw ->
                a = when {
                    raw.endsWith("%") -> (raw.dropLast(1).toFloatOrNull()?.div(100f) ?: 1f)
                    raw.toFloatOrNull()?.let { it <= 1f } == true -> raw.toFloat()
                    else -> raw.toFloatOrNull()?.div(255f) ?: 1f
                }.coerceIn(0f, 1f)
            }
            return ((a * 255).toInt() shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

/** 解析 Identifier：支持 `namespace:path` 或裸 `path`（默认 minecraft 命名空间） */
fun parseIdentifier(s: String): Identifier? {
    var t = s.trim()
    if (t.startsWith("url(") && t.endsWith(")")) t = t.substring(4, t.length - 1).trim()
    return if (t.isEmpty()) null else Identifier.tryParse(t)
}

/** 解析物品注册名 + 数量为 [ItemStack]，src 为空或物品不存在时返回空栈 */
fun parseItemStack(src: String?, count: String?): ItemStack {
    val id = src?.let { parseIdentifier(it) } ?: return ItemStack.EMPTY
    val item = BuiltInRegistries.ITEM.getValue(id) ?: return ItemStack.EMPTY
    if (item == Items.AIR) return ItemStack.EMPTY
    return ItemStack(item, count?.toIntOrNull()?.coerceAtLeast(1) ?: 1)
}
