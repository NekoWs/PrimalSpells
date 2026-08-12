package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.PrimalSpells

/**
 * 客户端 → 服务端：编杖请求。
 *
 * action：
 * - [ACTION_ADD]：将 spellId 加入 slotA（slotB 忽略）
 * - [ACTION_SWAP]：交换 slotA 与 slotB
 * - [ACTION_REMOVE]：移除 slotA 的法术，物品放入容器槽位 slotB
 */
data class EditWandPayload(
    val action: Int,
    val wandId: String,
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

        val ID = CustomPacketPayload.Type<EditWandPayload>(
            Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "edit_wand")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, EditWandPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, EditWandPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf) = EditWandPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readBoolean()
                )
                override fun encode(buf: RegistryFriendlyByteBuf, p: EditWandPayload) {
                    buf.writeVarInt(p.action)
                    buf.writeUtf(p.wandId)
                    buf.writeVarInt(p.slotA)
                    buf.writeVarInt(p.slotB)
                    buf.writeUtf(p.spellId)
                    buf.writeBoolean(p.silent)
                }
            }
    }
}
