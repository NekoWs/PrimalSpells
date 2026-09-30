package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells

/**
 * 服务端 → 客户端：法杖展示数值（HUD 与 tooltip 用）。
 *
 * 只同步**展示需要**的标量；槽位内容由物品组件（`WAND_SPELLS`）同步，
 * 所以这里不再冗余携带 `size`（物品 LORE 里已经写了）。
 *
 * ⚠️ 冷却值是**小数 tick**（第 26 轮双延迟改成毫秒后，计数器变 Double）：
 * 卡牌贡献整数、法杖自己的延迟折成小数，负余数用于「毫秒级平均节奏」（见 `Wand.spell`）。
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
                    buf.readDouble()
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
                }
            }
    }

    override fun type() = ID
}
