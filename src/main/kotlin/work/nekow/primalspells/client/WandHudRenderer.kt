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
    private var manaPersistTicks: Int = 0

    private const val HOLD_DURATION: Int = 10
    private const val FADE_DURATION: Int = 5

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
            if (manaPersistTicks > 0) manaPersistTicks--
        }

        val resetTarget = HOLD_DURATION + FADE_DURATION
        if (s.currentDelay > 0) delayPersistTicks = resetTarget
        if (s.currentRecharge > 0) rechargePersistTicks = resetTarget
        if (s.maxMana > 0 && s.currentMana < s.maxMana) manaPersistTicks = resetTarget

        val showMana = s.maxMana > 0 && manaPersistTicks > 0
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

        if (showMana) {
            val alpha = fadeAlpha(s.currentMana < s.maxMana, manaPersistTicks)
            drawBar(g, cx - bw / 2, cy, bw, bh,
                (s.currentMana / s.maxMana).toFloat(),
                alphaColor(0xFF_33_99_FF.toInt(), alpha), alpha)
        }
        val yOff = cy + bh + gap
        if (showDelay) {
            val progress = if (s.currentDelay > 0)
                1 - (s.currentDelay.toFloat() / s.lastDelay).coerceIn(0f, 1f) else 1f
            val alpha = fadeAlpha(s.currentDelay > 0, delayPersistTicks)
            drawBar(g, cx - bw / 2, yOff, bw, bh, progress,
                alphaColor(0xFF_FF_AA_00.toInt(), alpha), alpha)
        } else if (showRecharge) {
            val progress = if (s.currentRecharge > 0)
                1 - (s.currentRecharge.toFloat() / s.lastRecharge).coerceIn(0f, 1f) else 1f
            val alpha = fadeAlpha(s.currentRecharge > 0, rechargePersistTicks)
            drawBar(g, cx - bw / 2, yOff, bw, bh, progress,
                alphaColor(0xFF_33_55_CC.toInt(), alpha), alpha)
        }
    }

    private fun fadeAlpha(active: Boolean, persistTicks: Int): Float = when {
        active -> 1f
        persistTicks > FADE_DURATION -> 1f
        else -> (persistTicks.toFloat() / FADE_DURATION).coerceIn(0f, 1f)
    }

    private fun alphaColor(baseColor: Int, alpha: Float): Int {
        val a = (alpha * 0xFF).toInt().coerceIn(0, 255)
        return (a shl 24) or (baseColor and 0x00FFFFFF)
    }

    private fun drawBar(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Float, color: Int, alpha: Float = 1f) {
        val fill = (w * r.coerceIn(0f, 1f)).roundToInt()
        g.fill(x, y, x + w, y + h, alphaColor(0x40_44_44_44.toInt(), alpha))
        if (fill > 0) g.fill(x, y, x + fill, y + h, color)
    }
}
