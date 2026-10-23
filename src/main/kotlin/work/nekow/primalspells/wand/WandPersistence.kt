package work.nekow.primalspells.wand

import kotlin.math.ceil
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.SpellRegistry

/** 法杖NBT兼容边界；先恢复配置，再初始化实例、被动私有状态和牌堆游标。 */
internal object WandPersistence {
    fun save(wand: Wand): CompoundTag = with(wand) {
        val tag = CompoundTag()
        tag.putString("id", id)
        tag.putDouble("mana", mana)
        tag.putInt("delay_ticks", delayTicks().toInt())
        tag.putInt("recharge_ticks", rechargeTicks().toInt())
        tag.putDouble("charge_tick", charge)
        tag.putInt("cast", cast)
        tag.putInt("size", size)
        tag.putDouble("status_mana", status.mana)
        tag.putDouble("status_delay", status.delay)
        tag.putDouble("status_recharge", status.recharge)
        tag.put("innate", pileTag(innate))
        // 空槽与被移除的ID都占据原位置，不能让后面的卡向前移动。
        val slots = ListTag()
        repeat(maxOf(size, magics.size, 1)) { i ->
            slots.add(StringTag.valueOf(magics.getOrNull(i)?.id.orEmpty()))
        }
        tag.put("magics", slots)
        tag.put("magic_states", saveMagicStates(wand))
        tag.put("draw_pile", pileTag(drawPile))
        tag.put("hand", pileTag(hand))
        tag.put("discard_pile", pileTag(discardPile))
        tag
    }

    fun load(tag: CompoundTag): Wand {
        val wand = Wand(tag.getStringOr("id", ""))
        wand.mana = tag.getDoubleOr("mana", 0.0)
        wand.delayTickCount = readTicks(tag, "delay")
        wand.rechargeTickCount = readTicks(tag, "recharge")
        wand.charge = tag.getDoubleOr("charge_tick", tag.getDoubleOr("charge", 0.0) / 20.0)
        wand.cast = tag.getIntOr("cast", 1)
        wand.size = tag.getIntOr("size", 9)
        wand.status.mana = tag.getDoubleOr("status_mana", 0.0)
        wand.clampMana()
        wand.status.delay = ceil(tag.getDoubleOr("status_delay", 0.0).coerceAtLeast(0.0))
        wand.status.recharge = ceil(tag.getDoubleOr("status_recharge", 0.0).coerceAtLeast(0.0))
        wand.innate += magicsFrom(tag.getListOrEmpty("innate"))
        tag.getListOrEmpty("magics").forEach {
            val id = (it as StringTag).asString().get()
            wand.magics += if (id.isEmpty()) null else SpellRegistry.create(id)
        }
        // 钩子只能恢复卡自身字段，此时caster/wand尚未注入。
        wand.load()
        restoreMagicStates(wand, tag)
        restorePiles(wand, tag)
        return wand
    }

    /** 当前存档以刻为准；旧秒、毫秒与整数刻只在读档边界换算。 */
    private fun readTicks(tag: CompoundTag, name: String): Double = ceil(tag.getDoubleOr(name + "_ticks",
        if (tag.contains(name + "_sec")) tag.getDoubleOr(name + "_sec", 0.0) * Wand.TICKS_PER_SECOND
        else if (tag.contains(name + "_ms")) tag.getDoubleOr(name + "_ms", 0.0) / 50.0
        else tag.getDoubleOr(name, 0.0)).coerceAtLeast(0.0))

    private fun pileTag(pile: List<Magic>): ListTag = ListTag().also { list ->
        pile.forEach { list.add(StringTag.valueOf(it.id)) }
    }

    /** 牌堆不包含空槽，已删除的卡从游标序列中跳过。 */
    private fun magicsFrom(list: ListTag): List<Magic> =
        list.mapNotNull { SpellRegistry.create((it as StringTag).asString().get()) }

    private fun saveMagicStates(wand: Wand): ListTag {
        // 重建而非合并，取下的被动不能把陈旧状态留在存档里。
        wand.magicStates.clear()
        wand.loadedMagics.forEach { magic ->
            val state = CompoundTag()
            magic.saveRuntimeState(state)
            if (!state.isEmpty) wand.magicStates[magic.id] = state
        }
        return ListTag().also { list ->
            wand.magicStates.entries.sortedBy { it.key }.forEach { (id, state) ->
                list.add(CompoundTag().also { it.putString("id", id); it.put("state", state) })
            }
        }
    }

    private fun restoreMagicStates(wand: Wand, tag: CompoundTag) {
        wand.magicStates.clear()
        tag.getListOrEmpty("magic_states").forEach { element ->
            val entry = element as? CompoundTag ?: return@forEach
            val id = entry.getStringOr("id", "")
            if (id.isNotEmpty()) wand.magicStates[id] = entry.getCompoundOrEmpty("state")
        }
        wand.loadedMagics.forEach { magic ->
            wand.magicStates[magic.id]?.let { magic.loadRuntimeState(it) }
        }
    }

    private fun restorePiles(wand: Wand, tag: CompoundTag) {
        if (!tag.contains("draw_pile")) return
        val draw = magicsFrom(tag.getListOrEmpty("draw_pile"))
        val hand = magicsFrom(tag.getListOrEmpty("hand"))
        val discard = magicsFrom(tag.getListOrEmpty("discard_pile"))
        // 异常的全空牌堆退回load生成的初始配置，避免永久抽不到牌。
        if (draw.isEmpty() && hand.isEmpty() && discard.isEmpty() && wand.loadedMagics.isNotEmpty()) return
        wand.drawPile.clear(); wand.drawPile += draw
        wand.hand.clear(); wand.hand += hand
        wand.discardPile.clear(); wand.discardPile += discard
    }
}
