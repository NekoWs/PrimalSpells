package work.nekow.primalspells.ui

import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.ui.element.UiElement
import work.nekow.primalspells.ui.html.HtmlUiParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * HTML UI 加载器：根据 ui 名定位并加载对应的 HTML。
 *
 * 文件位于 `assets/primalspells/html/` 目录，命名规范：
 * ```
 * ui_<方块id>_<ui数字编号>.html
 * ```
 * 例如法杖编辑台的第一个界面：`ui_wand_editing_table_0.html`
 *
 * 加载优先级：
 * 1. **开发环境**：直接从源码目录 `src/main/resources/assets/primalspells/html/` 读取，
 *    修改文件后重新打开 UI（或 F3+T）即可生效，无需重启游戏。
 * 2. 回退到游戏资源管理器（正式 jar 包环境）。
 */
object HtmlUiLoader {

    /** 各 HTML 文件最近一次读取/记录的修改时间戳（dev 环境热更新检测用） */
    private val lastModifiedCache = mutableMapOf<String, Long>()

    /** 根据 ui 名生成对应 HTML 资源的 [Identifier] */
    fun identifier(blockId: String, uiNumber: Int): Identifier =
        Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "html/ui_${blockId}_${uiNumber}.html")

    /** 按任意文件名生成 HTML 资源 [Identifier]（如 `wand_info.html`） */
    fun fileIdentifier(name: String): Identifier =
        Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "html/$name")

    /** 加载并解析对应 UI 的元件列表 */
    fun load(blockId: String, uiNumber: Int, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()): List<UiElement> {
        val id = identifier(blockId, uiNumber)
        val devFile = devSourceFile(blockId, uiNumber)
        val source = if (devFile != null) Files.readString(devFile) else HtmlUiParser.load(id)
        return HtmlUiParser.parse(source, clicks)
    }

    /** 加载并解析为窗口形式（返回窗口尺寸与元件） */
    fun loadWindowed(blockId: String, uiNumber: Int, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()): HtmlUi {
        val id = identifier(blockId, uiNumber)
        val devFile = devSourceFile(blockId, uiNumber)
        val source = if (devFile != null) Files.readString(devFile) else HtmlUiParser.load(id)
        return HtmlUiParser.parseWindowed(source, clicks)
    }

    /** 按文件名加载并解析为窗口形式（浮窗等非方块 UI 使用，文件位于 `assets/primalspells/html/`） */
    fun loadWindowedFile(name: String, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()): HtmlUi {
        val devFile = devSourceFileByName(name)
        if (devFile != null) {
            touchModified(name, devFile)
            return HtmlUiParser.parseWindowed(Files.readString(devFile), clicks)
        }
        return HtmlUiParser.parseWindowed(HtmlUiParser.load(fileIdentifier(name)), clicks)
    }

    /**
     * dev 环境下检测 HTML 文件是否在最近一次读取后被修改（供浮窗等常驻 UI 热更新：
     * 返回 true 时调用方应重新 [loadWindowedFile] 并重建窗口）。
     * 首次调用仅记录时间戳并返回 false；打包环境（无源码目录）始终返回 false。
     */
    fun isModified(name: String): Boolean {
        val file = devSourceFileByName(name) ?: return false
        val t = try {
            Files.getLastModifiedTime(file).toMillis()
        } catch (e: Exception) {
            return false
        }
        val last = lastModifiedCache[name] ?: run {
            lastModifiedCache[name] = t
            return false
        }
        if (t != last) {
            lastModifiedCache[name] = t
            return true
        }
        return false
    }

    /** 记录 dev 文件当前修改时间戳（读取后调用，使首次 [isModified] 不误报） */
    private fun touchModified(name: String, file: Path) {
        val t = try {
            Files.getLastModifiedTime(file).toMillis()
        } catch (e: Exception) {
            return
        }
        lastModifiedCache[name] = t
    }

    /**
     * 定位开发环境下的源码 HTML 文件。
     * 工作目录为 `run/client`，向上两级为项目根；找不到时返回 null（回退资源管理器）。
     */
    private fun devSourceFile(blockId: String, uiNumber: Int): Path? =
        devSourceFileByName("ui_${blockId}_${uiNumber}.html")

    /** 按文件名定位开发环境源码 HTML（`src/main/resources/assets/primalspells/html/<name>`） */
    private fun devSourceFileByName(name: String): Path? {
        val workDir = Path.of("").toAbsolutePath()
        val projectRoot = workDir.parent?.parent ?: return null
        val file = projectRoot.resolve("src/main/resources/assets/primalspells/html/$name")
        return if (Files.exists(file)) file else null
    }
}
