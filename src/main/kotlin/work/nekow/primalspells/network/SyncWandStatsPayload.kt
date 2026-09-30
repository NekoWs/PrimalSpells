package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells

/**
 * 服务端 → 客户端：法杖展示数值（Alt 面板与 HUD 用）。
 *
 * 两组数值要分清（**别混成一个**）：
 * - **配置值**（`wandDelaySeconds` / `wandRechargeSeconds` / `cast` / `size` / `charge` / `maxMana`）：
 *   这根杖**是什么数值**，Alt 面板显示的就是它们（第 27 轮补上，之前面板只能显示下面的实时冷却）；
 * - **实时值**（`currentMana` / `currentDelay` / `currentRecharge` / `lastDelay` / `lastRecharge`）：
 *   当前剩多少冷却，HUD 的进度条用它们。
 *
 * 名词沿用 `Wand` 与既有语言键：`delay` = **施放延迟**、`recharge` = **充能时间**、
 * `charge` = **充能速度**、`cast` = **施放数**。
 *
 * ⚠️ 冷却值是**小数 tick**（毫秒折过来 + 负余数用于平均节奏，见 `Wand.spell`）。
 */
data class SyncWandStatsPayload(
    val wandId: String,
    val currentMana: Double,
    val maxMana: Double,
    val currentDelay: Double,
    val currentRecharge: Double,
    val cast: Int,
    val lastDelay: Double,
    val lastRecharge: Double,
    val charge: Double,
    val wandDelaySeconds: Double,
    val wandRechargeSeconds: Double,
    val size: Int,
) : CustomPacketPayload {

    companion object {
        val ID = CustomPacketPayload.Type<SyncWandStatsPayload>(
            Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "sync_wand_stats")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncWandStatsPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SyncWandStatsPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf) = SyncWandStatsPayload(
                    buf.readUtf(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt()
                )
                override fun encode(buf: RegistryFriendlyByteBuf, p: SyncWandStatsPayload) {
                    buf.writeUtf(p.wandId)
                    buf.writeDouble(p.currentMana)
                    buf.writeDouble(p.maxMana)
                    buf.writeDouble(p.currentDelay)
                    buf.writeDouble(p.currentRecharge)
                    buf.writeVarInt(p.cast)
                    buf.writeDouble(p.lastDelay)
                    buf.writeDouble(p.lastRecharge)
                    buf.writeDouble(p.charge)
                    buf.writeDouble(p.wandDelaySeconds)
                    buf.writeDouble(p.wandRechargeSeconds)
                    buf.writeVarInt(p.size)
                }
            }
    }

    override fun type() = ID
}
