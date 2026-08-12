package work.nekow.nekoui.html

/**
 * 一条 CSS 类规则：`.class1.class2[:hover] { prop: value; ... }`
 * 匹配要求元素同时拥有所有 [classes] 声明的类。
 */
class CssRule(
    val classes: List<String>,
    val hover: Boolean,
    val declarations: Map<String, String>,
) {
    fun matches(elementClasses: Collection<String>): Boolean = classes.all { it in elementClasses }
}

/**
 * CSS 注册表：解析 `<style>` 块并供元素匹配。
 *
 * 当前支持：
 * - 类选择器 `.name`、多类叠加 `.a.b`（须同时具备）、伪类 `:hover`
 * - 声明属性按出现顺序合并，后声明覆盖先声明
 *
 * 未实现（见 [work.nekow.nekoui.html.HtmlUiParser] KDoc TODO）：
 * 标签/id/通配选择器、:focus/:active、transition/animation/@keyframes、calc 等。
 */
class CssRegistry {

    private val rules = mutableListOf<CssRule>()

    /** 追加解析一段 CSS 文本（如 `<style>` 内容） */
    fun addRules(css: String) {
        rules += parseRules(css)
    }

    /** 全部规则（元素渲染时按自身 classList 动态匹配，支持运行期 classList 变更） */
    fun allRules(): List<CssRule> = rules.toList()

    companion object {
        fun parseRules(css: String): List<CssRule> {
            val result = mutableListOf<CssRule>()
            val text = css.replace(Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL)), "")
            val blockRe = Regex("([^{}]+)\\{([^{}]*)\\}", setOf(RegexOption.DOT_MATCHES_ALL))
            for (m in blockRe.findAll(text)) {
                val selector = m.groupValues[1].trim()
                val declarations = parseDeclarations(m.groupValues[2])
                parseSelector(selector)?.let { (classes, hover) ->
                    if (declarations.isNotEmpty()) result += CssRule(classes, hover, declarations)
                }
            }
            return result
        }

        private fun parseDeclarations(block: String): Map<String, String> {
            val map = linkedMapOf<String, String>()
            block.split(';').forEach { kv ->
                val idx = kv.indexOf(':')
                if (idx > 0) map[kv.take(idx).trim()] = kv.drop(idx + 1).trim()
            }
            return map
        }

        /**
         * 解析类选择器：`.a.b:hover` → ([a, b], true)。
         * 仅支持类选择器与 :hover 伪类；其他选择器（标签/id/其他伪类）返回 null（忽略该规则）。
         */
        private fun parseSelector(selector: String): Pair<List<String>, Boolean>? {
            val t = selector.trim()
            if (!t.startsWith(".")) return null
            val classes = mutableListOf<String>()
            var hover = false
            for (part in t.split('.')) {
                if (part.isEmpty()) continue
                val hoverIdx = part.indexOf(":hover")
                val name = if (hoverIdx >= 0) part.take(hoverIdx) else part
                if (name.isNotEmpty() && name.matches(Regex("[\\w-]+"))) classes += name
                if (hoverIdx >= 0) {
                    if (part.substring(hoverIdx) != ":hover") return null
                    hover = true
                }
            }
            return if (classes.isEmpty()) null else Pair(classes, hover)
        }
    }
}
