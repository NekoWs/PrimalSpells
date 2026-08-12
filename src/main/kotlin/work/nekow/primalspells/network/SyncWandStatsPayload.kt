package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells

data class SyncWandStatsPayload(
    val wandId: String,
    val currentMana: Double,
    val maxMana: Double,
    val currentDelay: Int,
    val currentRecharge: Int,
    val cast: Int,
    val lastDelay: Int,
    val lastRecharge: Int,
    val charge: Double,
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
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readVarInt()
                )
                override fun encode(buf: RegistryFriendlyByteBuf, p: SyncWandStatsPayload) {
                    buf.writeUtf(p.wandId)
                    buf.writeDouble(p.currentMana)
                    buf.writeDouble(p.maxMana)
                    buf.writeVarInt(p.currentDelay)
                    buf.writeVarInt(p.currentRecharge)
                    buf.writeVarInt(p.cast)
                    buf.writeVarInt(p.lastDelay)
                    buf.writeVarInt(p.lastRecharge)
                    buf.writeDouble(p.charge)
                    buf.writeVarInt(p.size)
                }
            }
    }

    override fun type() = ID
}
