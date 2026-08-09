package work.nekow.primalspells.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.network.SyncWandStatsPayload
import kotlin.math.roundToInt

@EventBusSubscriber(modid = PrimalSpells.MODID, value = [Dist.CLIENT])
object WandHudRenderer {

    @JvmField
    var stats: SyncWandStatsPayload? = null

    @JvmStatic
    @SubscribeEvent
    fun onRenderHud(event: RenderGuiLayerEvent.Post) {
        if (event.name != VanillaGuiLayers.CROSSHAIR) return
        val s = stats ?: return

        val showMana = s.maxMana > 0 && s.currentMana < s.maxMana
        val showDelay = s.currentDelay > 0
        val showRecharge = s.currentRecharge > 0
        if (!showMana && !showDelay && !showRecharge) return

        val g = event.guiGraphics
        val window = Minecraft.getInstance().window
        val cx = window.guiScaledWidth / 2
        val cy = window.guiScaledHeight / 2 + 12
        val bw = 22
        val bh = 2
        val gap = 2

        var y = cy
        if (showMana) {
            drawBar(g, cx - bw / 2, y, bw, bh, (s.currentMana / s.maxMana).toFloat(), 0xFF_33_99_FF.toInt())
            y += bh + gap
        }
        if (showDelay) {
            drawBar(g, cx - bw / 2, y, bw, bh,
                (s.currentDelay.toFloat() / 20).coerceIn(0f, 1f), 0xFF_FF_AA_00.toInt())
            y += bh + gap
        }
        if (showRecharge) {
            drawBar(g, cx - bw / 2, y, bw, bh,
                (s.currentRecharge.toFloat() / 20).coerceIn(0f, 1f), 0xFF_33_55_CC.toInt())
        }
    }

    private fun drawBar(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Float, color: Int) {
        val fill = (w * r.coerceIn(0f, 1f)).roundToInt()
        g.fill(x, y, x + w, y + h, 0x40_00_00_00.toInt())
        if (fill > 0) g.fill(x, y, x + fill, y + h, color)
    }
}
