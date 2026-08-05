package work.nekow.primalspells.wand

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.PrimalSpells.Companion.debug
import work.nekow.primalspells.magic.Magic
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.min

class Wand(var id: String) {
    val innate = arrayListOf<Magic>()
    val magics = arrayListOf<Magic>()
    var mana: Double = 0.0
    var delay: Int = 0
    var recharge: Int = 0
    var charge: Double = 0.0
    var cast = 1

    val drawPile = arrayListOf<Magic>()
    val hand = arrayListOf<Magic>()
    val discardPile = arrayListOf<Magic>()

    data class Status(
        var mana: Double = 0.0,
        var delay: Int = 0,
        var recharge: Int = 0
    )
    var status: Status = Status()

    fun load() {
        drawPile.clear()
        hand.clear()
        discardPile.clear()
        drawPile.addAll(innate + magics)
        drawPile.shuffle()
    }

    fun spell(caster: Entity) {
        draw(cast)
        caster.debug("Hand: ${hand.joinToString { it.javaClass.simpleName }}")
        castHand(caster, hand)
        discard()
    }

    /**
     * 施放手牌中的法术
     */
    private fun castHand(caster: Entity, hand: ArrayList<Magic>) {
        val effects = arrayListOf<BaseEffect>()
        for (magic in hand) {
            if (status.mana < magic.mana) continue
            status.mana -= magic.mana
            val clone = magic.clone()

            if (clone is TriggerSpell) {
                draw(clone.triggerCast)
                for (it in hand) {
                    if (status.mana < it.mana) continue
                    status.mana -= it.mana
                    clone.payload.add(it)
                }
                discard()
            }
            if (clone is Projectile) {
                clone.effects.addAll(effects)
                clone.caster = caster
                clone.spell()
                MagicManager.add(clone)
                continue
            }
            effects.addAll(magic.effects)
        }
    }

    private fun discard() {
        discardPile += hand
        hand.clear()
        reshuffleIfNeeded()
    }

    /**
     * 抓指定数量的牌
     */
    fun draw(budget: Int): Int {
        var remaining = budget
        var drawn = 0
        while (remaining > 0 && drawPile.isNotEmpty()) {
            val magic = drawPile.removeFirst()
            hand += magic
            remaining += magic.cast - 1
            drawn++
        }
        return drawn
    }

    fun reshuffle() {
        drawPile += discardPile
        discardPile.clear()
        drawPile.shuffle()
    }

    private fun reshuffleIfNeeded() {
        if (drawPile.isEmpty() && discardPile.isNotEmpty()) reshuffle()
    }

    fun tick() {
        if (status.delay > 0) status.delay--
        if (status.recharge > 0) status.recharge--
        if (status.mana < mana) status.mana = min(mana, status.mana + charge)
    }

    fun saveTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("id", id)
        tag.putDouble("mana", mana)
        tag.putInt("delay", delay)
        tag.putInt("recharge", recharge)
        tag.putDouble("charge", charge)
        tag.putInt("cast", cast)
        tag.putDouble("status_mana", status.mana)
        tag.putInt("status_delay", status.delay)
        tag.putInt("status_recharge", status.recharge)
        val innateList = ListTag()
        innate.forEach { innateList.add(StringTag.valueOf(it.id)) }
        tag.put("innate", innateList)
        val magicsList = ListTag()
        magics.forEach { magicsList.add(StringTag.valueOf(it.id)) }
        tag.put("magics", magicsList)
        return tag
    }

    companion object {
        fun fromTag(tag: CompoundTag): Wand {
            val wand = Wand(tag.getStringOr("id", ""))
            wand.mana = tag.getDoubleOr("mana", 0.0)
            wand.delay = tag.getIntOr("delay", 0)
            wand.recharge = tag.getIntOr("recharge", 0)
            wand.charge = tag.getDoubleOr("charge", 0.0)
            wand.cast = tag.getIntOr("cast", 1)
            wand.status.mana = tag.getDoubleOr("status_mana", 0.0)
            wand.status.delay = tag.getIntOr("status_delay", 0)
            wand.status.recharge = tag.getIntOr("status_recharge", 0)
            tag.getListOrEmpty("innate").forEach {
                Magic.create((it as StringTag).asString().get())?.let { m -> wand.innate += m }
            }
            tag.getListOrEmpty("magics").forEach {
                Magic.create((it as StringTag).asString().get())?.let { m -> wand.magics += m }
            }
            return wand
        }
    }
}
