package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells
import java.util.UUID

/** 瞄准被动的短租约，只驱动原版轮廓，不修改服务端实体的发光状态。 */
data class AimTargetPayload(val casterId: UUID, val wandId: String, val targetId: UUID?) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AimTargetPayload>(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "aim_target"))
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, AimTargetPayload> {
            override fun decode(buf: RegistryFriendlyByteBuf) = AimTargetPayload(buf.readUUID(), buf.readUtf(),
                if (buf.readBoolean()) buf.readUUID() else null)
            override fun encode(buf: RegistryFriendlyByteBuf, payload: AimTargetPayload) {
                buf.writeUUID(payload.casterId); buf.writeUtf(payload.wandId); buf.writeBoolean(payload.targetId != null)
                payload.targetId?.let(buf::writeUUID)
            }
        }
    }
    override fun type() = ID
}
