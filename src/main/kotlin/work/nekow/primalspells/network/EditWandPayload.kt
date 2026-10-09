package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** 悬浮编辑发送菜单/槽索引；创造光标只接收已注册散卡及合法数量，不接收任意组件或法杖数值。 */
data class EditWandPayload(val menuId: Int, val slot: Int, val wandId: String, val spellSlot: Int, val creativeSpell: String = "", val creativeCount: Int = -1) : CustomPacketPayload {
    override fun type() = ID
    companion object {
        val ID = CustomPacketPayload.Type<EditWandPayload>(Identifier.fromNamespaceAndPath("primalspells", "edit_wand"))
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, EditWandPayload> {
            override fun encode(b: RegistryFriendlyByteBuf, p: EditWandPayload) { b.writeVarInt(p.menuId); b.writeVarInt(p.slot); b.writeUtf(p.wandId); b.writeVarInt(p.spellSlot); b.writeUtf(p.creativeSpell); b.writeVarInt(p.creativeCount) }
            override fun decode(b: RegistryFriendlyByteBuf) = EditWandPayload(b.readVarInt(), b.readVarInt(), b.readUtf(128), b.readVarInt(), b.readUtf(128), b.readVarInt())
        }
    }
}
