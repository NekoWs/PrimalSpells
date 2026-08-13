package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells

/**
 * 服务端 → 客户端：能量盾状态同步（用于 HUD 盾条显示）。
 * 由能量盾本体在 onInventoryTick 中按状态变化发送。
 */
data class SyncShieldPayload(
    val wandId: String,
    val durability: Double,
    val maxDurability: Double,
    val pauseTicks: Int,
    val breakPauseTicks: Int,
) : CustomPacketPayload {

    companion object {
        val ID = CustomPacketPayload.Type<SyncShieldPayload>(
            Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "sync_shield")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncShieldPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SyncShieldPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf) = SyncShieldPayload(
                    buf.readUtf(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readVarInt()
                )
                override fun encode(buf: RegistryFriendlyByteBuf, p: SyncShieldPayload) {
                    buf.writeUtf(p.wandId)
                    buf.writeDouble(p.durability)
                    buf.writeDouble(p.maxDurability)
                    buf.writeVarInt(p.pauseTicks)
                    buf.writeVarInt(p.breakPauseTicks)
                }
            }
    }

    override fun type() = ID
}
