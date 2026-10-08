package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells

/** 一次成功施法的模型重音；观察者和施法者共用，粒子仍由PD播放。 */
data class WandCastPayload(val wandId: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<WandCastPayload>(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "wand_cast"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, WandCastPayload> = object : StreamCodec<RegistryFriendlyByteBuf, WandCastPayload> {
            override fun decode(buf: RegistryFriendlyByteBuf) = WandCastPayload(buf.readUtf())
            override fun encode(buf: RegistryFriendlyByteBuf, payload: WandCastPayload) { buf.writeUtf(payload.wandId) }
        }
    }
    override fun type() = ID
}
