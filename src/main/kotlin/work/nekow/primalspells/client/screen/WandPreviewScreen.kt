package work.nekow.primalspells.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.network.WandPreviewPayload
import work.nekow.primalspells.wand.WandPreview

/** V打开的只读抽牌树；定期向服务端刷新，滚轮浏览，不在客户端解析法术。 */
class WandPreviewScreen(private val wandId: String, private val parent: Screen?, initial: WandPreview? = null) : Screen(Component.translatable("preview.primalspells.title")) {
    private var age = 0
    private var scroll = 0
    var preview: WandPreview? = initial
        private set
    override fun init() { ClientPacketDistributor.sendToServer(WandPreviewPayload(wandId)) }
    override fun tick() { if (++age % 10 == 0) ClientPacketDistributor.sendToServer(WandPreviewPayload(wandId)) }
    /** 只接受本屏所请求的法杖，不建立跨世界静态缓存。 */
    fun accept(p: WandPreviewPayload) { if (p.wandId == wandId) preview = p.preview }
    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, partial: Float) {
        g.fill(8, 8, width - 8, height - 8, 0xEE101624.toInt())
        g.text(font, title, 18, 16, 0xFFD29AFF.toInt())
        val p = preview
        if (p == null) g.text(font, Component.translatable("preview.primalspells.wait"), 18, 33, -1)
        else {
            val summary = Component.translatable("preview.primalspells.summary", "%.1f".format(p.manaAfter), p.delay.toInt(), p.recharge.toInt(), p.shots)
            g.text(font, summary, 18, 31, 0xFF78E5F2.toInt())
            g.text(font, Component.translatable("preview.primalspells.help"), 18, 44, 0xFFB9B0CA.toInt())
            val lines = p.rows.flatMap { row ->
                val name = Component.translatable("item.primalspells.${row.id}").string
                val line = "${"  ".repeat(row.depth.coerceAtMost(12))}${if (row.accepted) "✓" else "×"} [${row.scope}] $name · ${Component.translatable("preview.primalspells.kind.${row.kind}").string} · ${row.mana} MP"
                listOf(line) + if (row.kind == "projectile" && row.modifiers.isNotEmpty())
                    listOf("${"  ".repeat(row.depth.coerceAtMost(12))}  ← " + row.modifiers.groupingBy { it }.eachCount().entries.joinToString { Component.translatable("item.primalspells.${it.key}").string + " ×${it.value}" }) else emptyList()
            } + p.reasons
            val visible = ((height - 88) / 12).coerceAtLeast(1)
            scroll = scroll.coerceIn(0, (lines.size - visible).coerceAtLeast(0))
            g.enableScissor(16, 60, width - 16, height - 23)
            lines.drop(scroll).take(visible).forEachIndexed { i, line -> g.text(font, line, 18, 62 + i * 12, if (line.trimStart().startsWith("×")) 0xFFFF6979.toInt() else -1) }
            g.disableScissor()
            g.text(font, "${scroll + 1}/${lines.size.coerceAtLeast(1)}", width - 70, height - 20, 0xFFB9B0CA.toInt())
        }
    }
    override fun mouseScrolled(x: Double, y: Double, dx: Double, dy: Double): Boolean { scroll = (scroll - dy.toInt() * 3).coerceAtLeast(0); return true }
    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen() = false
}

