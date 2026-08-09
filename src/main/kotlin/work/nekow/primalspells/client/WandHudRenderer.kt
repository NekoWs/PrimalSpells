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

    private var lastGameTime: Long = 0
    private var delayPersistTicks: Int = 0
    private var rechargePersistTicks: Int = 0
    private const val PERSIST_DURATION: Int = 5

    @JvmStatic
    @SubscribeEvent
    fun onRenderHud(event: RenderGuiLayerEvent.Post) {
        if (event.name != VanillaGuiLayers.CROSSHAIR) return
        val s = stats ?: return

        val gameTime = Minecraft.getInstance().level?.gameTime ?: 0
        if (gameTime != lastGameTime) {
            lastGameTime = gameTime
            if (delayPersistTicks > 0) delayPersistTicks--
            if (rechargePersistTicks > 0) rechargePersistTicks--
        }

        if (s.currentDelay > 0) delayPersistTicks = PERSIST_DURATION
        if (s.currentRecharge > 0) rechargePersistTicks = PERSIST_DURATION

        val showMana = s.maxMana > 0 && s.currentMana < s.maxMana
        val showDelay = s.currentDelay > 0 || delayPersistTicks > 0
        val showRecharge = s.currentRecharge > 0 || rechargePersistTicks > 0
        if (!showMana && !showDelay && !showRecharge) return

        val g = event.guiGraphics
        val window = Minecraft.getInstance().window
        val cx = window.guiScaledWidth / 2
        val cy = window.guiScaledHeight / 2 + 12
        val bw = 22
        val bh = 2
        val gap = 1

        var y = cy
        if (showMana) {
            drawBar(g, cx - bw / 2, y, bw, bh, (s.currentMana / s.maxMana).toFloat(), 0xFF_33_99_FF.toInt())
            y += bh + gap
        }
        if (showDelay) {
            val progress = if (s.currentDelay > 0)
                1 - (s.currentDelay.toFloat() / s.lastDelay).coerceIn(0f, 1f) else 1f
            drawBar(g, cx - bw / 2, y, bw, bh, progress, 0xFF_FF_AA_00.toInt())
        } else if (showRecharge) {
            val progress = if (s.currentRecharge > 0)
                1 - (s.currentRecharge.toFloat() / s.lastRecharge).coerceIn(0f, 1f) else 1f
            drawBar(g, cx - bw / 2, y, bw, bh, progress, 0xFF_33_55_CC.toInt())
        }
    }

    private fun drawBar(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Float, color: Int) {
        val fill = (w * r.coerceIn(0f, 1f)).roundToInt()
        g.fill(x, y, x + w, y + h, 0x40_00_00_00.toInt())
        if (fill > 0) g.fill(x, y, x + fill, y + h, color)
    }
}
