package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.wand.WandPreview

/** 双向预览包：空结果为请求；服务端只允许查看当前菜单/背包内真实法杖。 */
data class WandPreviewPayload(val wandId: String, val preview: WandPreview? = null) : CustomPacketPayload {
    override fun type() = ID
    companion object {
        val ID = CustomPacketPayload.Type<WandPreviewPayload>(Identifier.fromNamespaceAndPath("primalspells", "wand_preview"))
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, WandPreviewPayload> {
            override fun encode(b: RegistryFriendlyByteBuf, p: WandPreviewPayload) {
                b.writeUtf(p.wandId); b.writeBoolean(p.preview != null)
                val v = p.preview ?: return
                b.writeDouble(v.manaAfter); b.writeDouble(v.delay); b.writeDouble(v.recharge); b.writeVarInt(v.shots)
                b.writeVarInt(v.reasons.size); v.reasons.forEach { b.writeUtf(it) }
                b.writeVarInt(v.rows.size)
                v.rows.forEach { r ->
                    b.writeUtf(r.id); b.writeUtf(r.scope); b.writeVarInt(r.depth); b.writeUtf(r.kind)
                    b.writeDouble(r.mana); b.writeBoolean(r.accepted)
                    b.writeVarInt(r.modifiers.size); r.modifiers.forEach { b.writeUtf(it) }
                }
                b.writeVarInt(v.slots.size)
                v.slots.forEach { b.writeUtf(it.id); b.writeUtf(it.scope); b.writeVarInt(it.remainingUses) }
            }
            override fun decode(b: RegistryFriendlyByteBuf): WandPreviewPayload {
                val id = b.readUtf(128)
                if (!b.readBoolean()) return WandPreviewPayload(id)
                val mana = b.readDouble(); val delay = b.readDouble(); val recharge = b.readDouble(); val shots = b.readVarInt()
                fun count() = b.readVarInt().also { require(it in 0..4096) }
                val reasons = List(count()) { b.readUtf() }
                val rows = List(count()) {
                    WandPreview.Row(b.readUtf(), b.readUtf(), b.readVarInt(), b.readUtf(), b.readDouble(), b.readBoolean(), List(count()) { b.readUtf() })
                }
                val slots = List(count()) { WandPreview.Slot(b.readUtf(), b.readUtf(), b.readVarInt()) }
                return WandPreviewPayload(id, WandPreview(rows, mana, delay, recharge, reasons, shots, slots))
            }
        }
    }
}
