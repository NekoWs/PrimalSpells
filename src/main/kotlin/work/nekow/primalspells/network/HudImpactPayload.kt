package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells

/** 服务端确认的一次法术命中或击杀，只发送给施法玩家；客户端逐次独立显示。 */
data class HudImpactPayload(val killed: Boolean) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<HudImpactPayload>(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "hud_impact"))
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, HudImpactPayload> {
            override fun decode(buf: RegistryFriendlyByteBuf) = HudImpactPayload(buf.readBoolean())
            override fun encode(buf: RegistryFriendlyByteBuf, payload: HudImpactPayload) { buf.writeBoolean(payload.killed) }
        }
    }
    override fun type() = ID
}
