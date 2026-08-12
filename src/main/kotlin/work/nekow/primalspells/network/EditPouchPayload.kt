package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.PrimalSpells

/**
 * 客户端 → 服务端：修改法术小包内容。
 *
 * [pouchId]：小包唯一标识（见 SpellPouchItem.POUCH_ID 组件）。
 * 服务端在玩家背包中按 pouchId 定位小包——容器菜单切换会使槽位索引失效，故不用索引。
 * action：
 * - [ACTION_ADD]：将 spellId 加入包内 slotA
 * - [ACTION_SWAP]：交换包内 slotA 与 slotB
 * - [ACTION_REMOVE]：移除包内 slotA，物品放入容器槽位 slotB（-1 则放入玩家背包）
 */
data class EditPouchPayload(
    val action: Int,
    val pouchId: String,
    val slotA: Int,
    val slotB: Int,
    val spellId: String,
    val silent: Boolean = false,
) : CustomPacketPayload {

    override fun type() = ID

    fun send() {
        ClientPacketDistributor.sendToServer(this)
    }

    companion object {
        const val ACTION_ADD = 0
        const val ACTION_SWAP = 1
        const val ACTION_REMOVE = 2

        val ID = CustomPacketPayload.Type<EditPouchPayload>(
            Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "edit_pouch")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, EditPouchPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, EditPouchPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf) = EditPouchPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readBoolean()
                )
                override fun encode(buf: RegistryFriendlyByteBuf, p: EditPouchPayload) {
                    buf.writeVarInt(p.action)
                    buf.writeUtf(p.pouchId)
                    buf.writeVarInt(p.slotA)
                    buf.writeVarInt(p.slotB)
                    buf.writeUtf(p.spellId)
                    buf.writeBoolean(p.silent)
                }
            }
    }
}
