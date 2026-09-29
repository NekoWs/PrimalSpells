package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.PrimalSpells

/**
 * 客户端 → 服务端：同步「当前选中的法术槽位」。
 *
 * 法杖与法术小包共用同一个包，用 [pouch] 区分：
 * - `pouch = false`：按 `wand_id` 匹配物品栈，写入 `WAND_SELECTED_SLOT`；
 * - `pouch = true`：按 `pouch_id` 匹配物品栈，写入 `POUCH_SELECTED_SLOT`。
 *
 * 为什么必须发这个包：物品组件只有服务端权威，客户端本地 `set` 不会同步，
 * 而服务端的「物品对上物品」装卸逻辑（`WandItem`/`SpellPouchItem.overrideOtherStackedOnMe`）
 * 读的正是这个槽位组件。
 */
data class SelectSpellSlotPayload(
    val targetId: String,
    val slot: Int,
    val pouch: Boolean,
) : CustomPacketPayload {

    override fun type() = ID

    fun send() {
        ClientPacketDistributor.sendToServer(this)
    }

    companion object {
        val ID = CustomPacketPayload.Type<SelectSpellSlotPayload>(
            Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "select_spell_slot")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SelectSpellSlotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SelectSpellSlotPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf) =
                    SelectSpellSlotPayload(buf.readUtf(), buf.readVarInt(), buf.readBoolean())
                override fun encode(buf: RegistryFriendlyByteBuf, p: SelectSpellSlotPayload) {
                    buf.writeUtf(p.targetId)
                    buf.writeVarInt(p.slot)
                    buf.writeBoolean(p.pouch)
                }
            }
    }
}
