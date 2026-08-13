package work.nekow.primalspells.client

import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.network.SyncShieldPayload
import kotlin.math.roundToInt

/**
 * 能量盾 HUD 条：与充能/延迟条同风格，渲染在准星下方（CROSSHAIR 层），任何模式可见。
 *
 * - 蓝色：护盾正常（填充比例 = 当前耐久 / 最大耐久）
 * - 橙色：护盾破碎恢复中（填充比例 = 破盾暂停进度，来自服务端 SyncShieldPayload）
 *
 * 数据按持有法杖的 wandId 缓存（与 WandHudRenderer.statsMap 同模式）：
 * 服务端仅在手持含能量盾的法杖时推送，换杖/放下后不再渲染。
 */
@EventBusSubscriber(modid = PrimalSpells.MODID, value = [Dist.CLIENT])
object ShieldHudRenderer {

    @JvmField
    val shieldMap: MutableMap<String, SyncShieldPayload> = hashMapOf()

    fun putShield(payload: SyncShieldPayload) {
        shieldMap[payload.wandId] = payload
    }

    /** 当前手持（主手/副手）法杖中已同步过护盾数据的 wandId */
    private fun heldShieldWandId(): String? {
        val player = Minecraft.getInstance().player ?: return null
        for (hand in listOf(player.mainHandItem, player.offhandItem)) {
            val wandId = hand.get(ModItems.WAND_ID)?.wandId ?: continue
            if (wandId in shieldMap) return wandId
        }
        return null
    }

    @JvmStatic
    @SubscribeEvent
    fun onRenderHud(event: RenderGuiLayerEvent.Post) {
        if (event.name != VanillaGuiLayers.CROSSHAIR) return
        val payload = heldShieldWandId()?.let { shieldMap[it] } ?: return
        val mc = Minecraft.getInstance()

        // 准星下方：充能/延迟条在 center+12 / +15，盾条在其下方（center+18）
        val cx = mc.window.guiScaledWidth / 2
        val cy = mc.window.guiScaledHeight / 2 + 18
        val barW = 60
        val barH = 3

        val ratio: Float
        val color: Int
        if (payload.pauseTicks > 0) {
            ratio = 1f - (payload.pauseTicks.toFloat() / payload.breakPauseTicks).coerceIn(0f, 1f)
            color = 0xFF_FF_AA_00.toInt()
        } else {
            ratio = (payload.durability / payload.maxDurability).toFloat().coerceIn(0f, 1f)
            color = 0xFF_55_AA_FF.toInt()
        }

        val g = event.guiGraphics
        g.fill(cx - barW / 2, cy, cx + barW / 2, cy + barH, 0x60_00_00_00.toInt())
        val fill = (barW * ratio).roundToInt()
        if (fill > 0) g.fill(cx - barW / 2, cy, cx - barW / 2 + fill, cy + barH, color)
    }
}
