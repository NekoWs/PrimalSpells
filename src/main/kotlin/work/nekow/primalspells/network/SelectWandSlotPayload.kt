package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.PrimalSpells

/**
 * 客户端 → 服务端：同步法杖当前选中的法术槽位（滚轮选择）。
 *
 * 原实现直接在客户端 `slot.item.set(WAND_SELECTED_SLOT)`，但物品组件仅由服务端
 * 权威、客户端修改不会同步，导致服务端 [work.nekow.primalspells.item.WandItem]
 * 右键装卸仍读旧槽位。故改由本包显式同步。
 */
data class SelectWandSlotPayload(
    val wandId: String,
    val slot: Int,
) : CustomPacketPayload {

    override fun type() = ID

    fun send() {
        ClientPacketDistributor.sendToServer(this)
    }

    companion object {
        val ID = CustomPacketPayload.Type<SelectWandSlotPayload>(
            Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "select_wand_slot")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SelectWandSlotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SelectWandSlotPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf) =
                    SelectWandSlotPayload(buf.readUtf(), buf.readVarInt())
                override fun encode(buf: RegistryFriendlyByteBuf, p: SelectWandSlotPayload) {
                    buf.writeUtf(p.wandId)
                    buf.writeVarInt(p.slot)
                }
            }
    }
}
