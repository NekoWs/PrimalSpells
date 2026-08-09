package work.nekow.primalspells.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.network.SyncWandStatsPayload
import kotlin.math.roundToInt

@EventBusSubscriber(modid = PrimalSpells.MODID, value = [Dist.CLIENT])
object WandHudRenderer {

    @JvmField
    val statsMap: MutableMap<String, SyncWandStatsPayload> = hashMapOf()

    fun getStats(wandId: String): SyncWandStatsPayload? = statsMap[wandId]

    fun putStats(payload: SyncWandStatsPayload) {
        statsMap[payload.wandId] = payload
    }

    private fun getHeldWandStats(): SyncWandStatsPayload? {
        val player = Minecraft.getInstance().player ?: return null
        for (hand in listOf(player.mainHandItem, player.offhandItem)) {
            val wandId = hand.get(ModItems.WAND_ID)?.wandId ?: continue
            return statsMap[wandId]
        }
        return null
    }

    private var lastGameTime: Long = 0
    private var delayPersistTicks: Int = 0
    private var rechargePersistTicks: Int = 0
    private var manaPersistTicks: Int = 0

    private var displayDelayProgress: Float = 0f
    private var displayRechargeProgress: Float = 0f
    private var displayManaProgress: Float = 1f

    private const val HOLD_DURATION: Int = 10
    private const val FADE_DURATION: Int = 5
    private const val SMOOTH_FACTOR: Float = 0.25f

    @JvmStatic
    @SubscribeEvent
    fun onRenderHud(event: RenderGuiLayerEvent.Post) {
        if (event.name != VanillaGuiLayers.CROSSHAIR) return
        val s = getHeldWandStats() ?: return

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

        val targetDelay = if (s.currentDelay > 0) {
            1f - (s.currentDelay.toFloat() / s.lastDelay).coerceIn(0f, 1f)
        } else 1f
        val targetRecharge = if (s.currentRecharge > 0) {
            1f - (s.currentRecharge.toFloat() / s.lastRecharge).coerceIn(0f, 1f)
        } else 1f
        val targetMana = if (s.maxMana > 0) (s.currentMana / s.maxMana).toFloat() else 1f

        displayDelayProgress += (targetDelay - displayDelayProgress) * SMOOTH_FACTOR
        displayRechargeProgress += (targetRecharge - displayRechargeProgress) * SMOOTH_FACTOR
        displayManaProgress += (targetMana - displayManaProgress) * SMOOTH_FACTOR

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
                displayManaProgress,
                alphaColor(0xFF_33_99_FF.toInt(), alpha), alpha)
        }
        val yOff = cy + bh + gap
        if (showDelay) {
            val alpha = fadeAlpha(s.currentDelay > 0, delayPersistTicks)
            drawBar(g, cx - bw / 2, yOff, bw, bh, displayDelayProgress,
                alphaColor(0xFF_FF_AA_00.toInt(), alpha), alpha)
        } else if (showRecharge) {
            val alpha = fadeAlpha(s.currentRecharge > 0, rechargePersistTicks)
            drawBar(g, cx - bw / 2, yOff, bw, bh, displayRechargeProgress,
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
