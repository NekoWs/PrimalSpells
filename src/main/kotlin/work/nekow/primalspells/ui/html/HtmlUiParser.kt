package work.nekow.primalspells.ui.html

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.nekow.primalspells.ui.HtmlUi
import work.nekow.primalspells.ui.UiScreen
import work.nekow.primalspells.ui.element.TextAlign
import work.nekow.primalspells.ui.element.UiButton
import work.nekow.primalspells.ui.element.UiElement
import work.nekow.primalspells.ui.element.UiImage
import work.nekow.primalspells.ui.element.UiLabel
import work.nekow.primalspells.ui.element.UiSlot
import work.nekow.primalspells.ui.element.UiWindow

/**
 * 极简 HTML → MC UI 映射器。
 *
 * 支持标签：
 * - `div`       → [UiWindow] 面板（子元素相对面板定位）
 * - `button`    → [UiButton] 按钮（onclick="close" 关闭，或自定义回调名）
 * - `label`/`span`/`p`/`h1`~`h3` → [UiLabel] 文本
 * - `img`       → [UiImage] 图片（src 为资源包贴图路径，如 `primalspells:textures/item/wand.png`）
 * - `slot`      → [UiSlot] 物品槽位（src 为物品注册名如 `primalspells:wand`，count 为数量，留空为空白槽位）
 * - `br`        → 文本换行
 *
 * 支持 style 属性：
 * - `left` / `top` / `width` / `height`：像素（可省略 px）或百分比（相对父元素；顶层相对屏幕）
 * - `color` / `background` / `border-color` / `hover-background` / `hover-color`：颜色
 *   （#RGB、#RRGGBB，或命名颜色：white/black/gray/red/green/blue/yellow/orange/purple/cyan/transparent）
 * - `text-align`: left / center / right
 * - `background-image`: url(...) 或直接贴图路径（div 背景图）
 * - `title`：面板标题文字
 * - `enabled="false"`：按钮禁用
 *
 * 示例：
 * ```
 * <div style="left:20%;top:15%;width:60%;height:70%;background:#1a1a1e;border-color:#4a4a55" title="法杖编辑台">
 *     <label style="left:0;top:28;width:100%;text-align:center;color:white">法术编辑</label>
 *     <slot src="primalspells:wand" style="left:10;top:50"/>
 *     <slot src="primalspells:fireball" count="5" style="left:34;top:50"/>
 *     <button style="left:10;top:130;width:70;height:20" onclick="close">关闭</button>
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
     * 其 `width`/`height`（像素或百分比）即窗口尺寸，`left`/`top` 忽略（窗口自动居中）。
     * 子元素以窗口内容区左上角为原点定位。
     */
    fun parseWindowed(html: String, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()): HtmlUi {
        val roots = HtmlParser(html).parse()
        val win = Minecraft.getInstance().window
        val screenW = win.guiScaledWidth
        val screenH = win.guiScaledHeight
        val elements = mutableListOf<UiElement>()

        val windowNode = roots.firstOrNull { it.tag == "div" }
        if (windowNode == null) {
            roots.forEach { convert(it, 0, 0, screenW, screenH, clicks, elements) }
            return HtmlUi(elements, screenW, screenH)
        }

        val style = Style.parse(windowNode.attrs["style"])
        val w = style.dim("width", screenW) ?: 0
        val h = style.dim("height", screenH) ?: 0
        convert(windowNode, 0, 0, w, h, clicks, elements)
        return HtmlUi(elements, w, h)
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
        out: MutableList<UiElement>
    ) {
        val style = Style.parse(node.attrs["style"])
        val x = parentX + (style.dim("left", parentW) ?: 0)
        val y = parentY + (style.dim("top", parentH) ?: 0)
        val w = style.dim("width", parentW) ?: 0
        val h = style.dim("height", parentH) ?: 0
        val text = node.text()
        val align = style.align()

        when (node.tag) {
            "br" -> Unit

            "div" -> {
                out += UiWindow(
                    x, y, w, h,
                    title = node.attrs["title"]?.let { Component.literal(it) } ?: Component.empty(),
                    background = style.colorOf("background") ?: 0xF0_1A_1A_1E.toInt(),
                    borderColor = style.colorOf("border-color") ?: 0xFF_3D_3D_42.toInt(),
                    image = style.identifier("background-image"),
                )
                node.children.forEach { convert(it, x, y, w, h, clicks, out) }
            }

            "img" -> {
                val src = node.attrs["src"]?.let { parseIdentifier(it) } ?: return
                out += UiImage(x, y, w, h, src)
            }

            "slot" -> {
                val stack = parseItemStack(node.attrs["src"], node.attrs["count"])
                out += UiSlot(
                    x, y,
                    width = if (w > 0) w else 18,
                    height = if (h > 0) h else 18,
                    stack = stack,
                )
            }

            "button" -> {
                val actionName = node.attrs["onclick"] ?: return
                val action: UiScreen.() -> Unit = when {
                    actionName == "close" -> { s: UiScreen -> s.close() }
                    clicks.containsKey(actionName) -> clicks.getValue(actionName)
                    else -> { s: UiScreen -> s.close() }
                }
                out += UiButton(
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
                )
            }

            "label", "span", "p", "h1", "h2", "h3" -> {
                out += UiLabel(
                    Component.literal(text),
                    x, y, w, h,
                    color = style.colorOf("color") ?: 0xFF_E0E0E0.toInt(),
                    align = align,
                )
            }
        }
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
