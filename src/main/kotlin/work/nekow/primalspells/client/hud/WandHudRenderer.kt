package work.nekow.primalspells.client.hud

import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.client.screen.HudEditorScreen
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.network.SyncWandStatsPayload
import work.nekow.primalspells.registry.ModItems
import kotlin.math.*

/** 客户端数值缓存与按游戏时间求值的HUD动画；帧率不影响缓动速度。 */
@EventBusSubscriber(modid = PrimalSpells.MODID, value = [Dist.CLIENT])
object WandHudRenderer {
    @JvmField val statsMap: MutableMap<String, SyncWandStatsPayload> = hashMapOf()
    private val received = hashMapOf<String, Double>()
    private var identity: String? = null
    private var lastTime = Double.NaN
    private var presence = 0.0
    private var delayAlpha = 0.0
    private var rechargeAlpha = 0.0
    private var hurtAlpha = 0.0
    private var displayedMana = 0.0
    private var lastFrame: WandHudFrame? = null
    private val impacts = HudImpactAnimations()

    /** tooltip与录制检查共用的服务端快照。 */
    fun getStats(wandId: String) = statsMap[wandId]
    /** 最近实际绘制的状态；开发录制用它确认真实伤害通知已经到达HUD。 */
    fun snapshot(): WandHudFrame? = lastFrame
    /** 网络入口记录本地接收时间，冷却子帧外推不会依赖包发送频率。 */
    fun putStats(payload: SyncWandStatsPayload) { statsMap[payload.wandId] = payload; received[payload.wandId] = clock() }
    /** 真正接受的法术伤害回报；每次通知独立播放，不覆盖或延长其它反馈。 */
    fun impact(killed: Boolean) { impacts.add(killed, clock()) }
    /** 退出世界时清缓存与动画，不清本机布局。 */
    fun clear() {
        statsMap.clear(); received.clear(); identity = null; lastTime = Double.NaN; lastFrame = null
        presence = 0.0; delayAlpha = 0.0; rechargeAlpha = 0.0; hurtAlpha = 0.0
        impacts.clear()
    }
    private fun clock(): Double {
        val mc = Minecraft.getInstance()
        return (mc.level?.gameTime ?: 0).toDouble() + mc.deltaTracker.getGameTimeDeltaPartialTick(false)
    }
    private fun held(): SyncWandStatsPayload? {
        val player = Minecraft.getInstance().player ?: return null
        val hands = if (player.isUsingItem) listOf(player.useItem, player.mainHandItem, player.offhandItem)
            else listOf(player.mainHandItem, player.offhandItem)
        val stack = hands.firstOrNull { it.item is WandItem } ?: return null
        return stack.get(ModItems.WAND_ID)?.wandId?.let { statsMap[it] }
    }

    /** 给定时钟的一帧状态，供真实绘制与帧率无关的回归检查使用。 */
    fun frame(s: SyncWandStatsPayload?, now: Double): WandHudFrame? {
        var dt = if (lastTime.isFinite()) (now - lastTime).coerceIn(0.0, 4.0) else 0.0
        lastTime = now
        if (s != null && identity != s.wandId) {
            identity = s.wandId; presence = 0.0; delayAlpha = 0.0; rechargeAlpha = 0.0; hurtAlpha = 0.0
            displayedMana = s.currentMana
            dt = 0.0
        }
        fun approach(value: Double, target: Double, ticks: Double) = value + (target - value) * (1 - exp(-dt / ticks))
        presence = approach(presence, if (s != null) 1.0 else 0.0, 1.3)
        val feedback = impacts.sample(now).takeIf { HudSettings.current.hitFeedback } ?: emptyList()
        if (s == null) {
            val previous = lastFrame ?: return null
            if (presence < 0.01) { identity = null; lastFrame = null; return null }
            return previous.copy(presence = presence, impacts = feedback).also { lastFrame = it }
        }
        val elapsed = (now - (received[s.wandId] ?: now)).coerceIn(0.0, 20.0)
        val delay = max(0.0, s.currentDelay - elapsed)
        val recharge = max(0.0, s.currentRecharge - elapsed)
        fun progress(left: Double, total: Double) = if (total > 0) (1 - left / total).coerceIn(0.0, 1.0) else 1.0
        delayAlpha = approach(delayAlpha, if (delay > 0) 1.0 else 0.0, if (delay > 0) 0.7 else 2.0)
        rechargeAlpha = approach(rechargeAlpha, if (recharge > 0) 1.0 else 0.0, if (recharge > 0) 0.7 else 2.0)
        displayedMana = approach(displayedMana, s.currentMana, 0.8)
        if (abs(displayedMana - s.currentMana) < 0.05) displayedMana = s.currentMana
        val settings = HudSettings.current
        val player = Minecraft.getInstance().player
        hurtAlpha = approach(hurtAlpha, (player?.hurtTime ?: 0) / 10.0, 0.8)
        return WandHudFrame(displayedMana.coerceIn(0.0, s.maxMana.coerceAtLeast(0.0)), s.maxMana,
            delay, recharge, progress(delay, s.lastDelay), progress(recharge, s.lastRecharge),
            manaBlocked = s.manaBlocked, presence = presence, delayAlpha = delayAlpha, rechargeAlpha = rechargeAlpha,
            impacts = feedback,
            hurtAngle = if (settings.damageDirection && player != null) Math.toRadians(player.hurtDir.toDouble() - 180) else null,
            hurtAlpha = hurtAlpha,
        ).also { lastFrame = it }
    }

    @JvmStatic @SubscribeEvent
    fun onRenderHud(event: RenderGuiLayerEvent.Post) {
        if (event.name != VanillaGuiLayers.CROSSHAIR) return
        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.gui.screen() is HudEditorScreen || mc.player?.isSpectator == true) return
        val now = clock(); val frame = frame(held(), now) ?: return
        val layout = HudSettings.current
        for (element in HudElement.entries) WandHudPainter.drawElement(event.guiGraphics, element,
            layout.box(element, mc.window.guiScaledWidth, mc.window.guiScaledHeight), frame, now)
    }
}
