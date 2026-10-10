package work.nekow.primalspells.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.network.WandPreviewPayload
import work.nekow.primalspells.wand.WandPreview

/** V打开的只读槽位预览；颜色区分作用域，定期向服务端刷新，不在客户端解析法术。 */
class WandPreviewScreen(private val wandId: String, private val parent: Screen?) : Screen(Component.translatable("preview.primalspells.title")) {
    private var age = 0
    var preview: WandPreview? = null
        private set
    override fun init() { ClientPacketDistributor.sendToServer(WandPreviewPayload(wandId)) }
    override fun tick() { if (++age % 10 == 0) ClientPacketDistributor.sendToServer(WandPreviewPayload(wandId)) }
    /** 只接受本屏所请求的法杖，不建立跨世界静态缓存。 */
    fun accept(p: WandPreviewPayload) { if (p.wandId == wandId) preview = p.preview }
    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, partial: Float) {
        val p = preview
        val slots = p?.slots.orEmpty()
        val grid = work.nekow.primalspells.client.tooltip.ClientWandSpellTooltip(
            work.nekow.primalspells.client.tooltip.WandSpellTooltip(slots.map { it.id }, wandId, slots))
        val panelWidth = (width - 16).coerceIn(1, 290)
        val panelHeight = 88 + grid.getHeight(font) + (p?.reasons?.size?.coerceAtMost(3) ?: 0) * 12
        val x = (width - panelWidth) / 2; val y = ((height - panelHeight) / 2).coerceAtLeast(8)
        g.fill(x, y, x + panelWidth, y + panelHeight, 0xEE101624.toInt())
        g.text(font, title, x + 12, y + 12, 0xFFD29AFF.toInt())
        if (p == null) g.text(font, Component.translatable("preview.primalspells.wait"), x + 12, y + 31, -1)
        else {
            val summary = Component.translatable("preview.primalspells.summary", "%.1f".format(p.manaAfter), p.delay.toInt(), p.recharge.toInt(), p.shots)
            g.text(font, summary, x + 12, y + 30, 0xFF78E5F2.toInt())
            grid.extractImage(font, x + (panelWidth - grid.getWidth(font)) / 2, y + 49, grid.getWidth(font), grid.getHeight(font), g)
            g.enableScissor(x + 10, y + 10, x + panelWidth - 10, y + panelHeight - 6)
            g.text(font, Component.translatable("preview.primalspells.help"), x + 12, y + 56 + grid.getHeight(font), 0xFFB9B0CA.toInt())
            p.reasons.take(3).forEachIndexed { i, reason -> g.text(font, reason, x + 12, y + 72 + grid.getHeight(font) + i * 12, 0xFFFF6979.toInt()) }
            g.disableScissor()
        }
    }
    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen() = false
}

