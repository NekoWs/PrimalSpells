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

    /** 根据 ui 名生成对应 HTML 资源的 [Identifier] */
    fun identifier(blockId: String, uiNumber: Int): Identifier =
        Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "html/ui_${blockId}_${uiNumber}.html")

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

    /**
     * 定位开发环境下的源码 HTML 文件。
     * 工作目录为 `run/client`，向上两级为项目根；找不到时返回 null（回退资源管理器）。
     */
    private fun devSourceFile(blockId: String, uiNumber: Int): Path? {
        val workDir = Path.of("").toAbsolutePath()
        val projectRoot = workDir.parent?.parent ?: return null
        val file = projectRoot.resolve("src/main/resources/assets/primalspells/html/ui_${blockId}_${uiNumber}.html")
        return if (Files.exists(file)) file else null
    }
}
