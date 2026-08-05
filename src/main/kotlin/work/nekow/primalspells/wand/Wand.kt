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

    val drawPile = arrayListOf<SpellChain>()
    val hand = arrayListOf<SpellChain>()
    val discardPile = arrayListOf<SpellChain>()

    data class Status(
        var mana: Double = 0.0,
        var delay: Int = 0,
        var recharge: Int = 0
    )
    var status: Status = Status()

    /** 解析 innate + magics 为 SpellChain 列表，链接 TriggerSpell.payload，洗入抽牌堆。 */
    fun load() {
        drawPile.clear()
        hand.clear()
        discardPile.clear()
        drawPile.addAll(parseChains(innate + magics))
        drawPile.shuffle()
    }

    // TODO：Rebuild
    private fun parseChains(spells: List<Magic>): List<SpellChain> {
        // Step 1: 分组为 [修正器..., 投射物]
        val flat = arrayListOf<SpellChain>()
        var i = 0
        while (i < spells.size) {
            val mods = arrayListOf<Magic>()
            while (i < spells.size && spells[i] !is Projectile) {
                mods.add(spells[i])
                i++
            }
            if (i < spells.size) {
                flat.add(SpellChain(mods, spells[i] as Projectile))
                i++
            }
        }

        // Step 2: 链接 TriggerSpell → 下一个链
        for (j in 0 until flat.size - 1) {
            val proj = flat[j].projectile
            if (proj is TriggerSpell && proj.payload == null) {
                proj.payload = flat[j + 1]
            }
        }

        // Step 3: 只返回根链（不是别人 payload 的）
        return flat.filterIndexed { idx, _ ->
            idx == 0 || flat[idx - 1].projectile !is TriggerSpell
        }
    }

    fun spell(caster: Entity) {
        draw(cast)
        caster.debug("Hand: ${hand.joinToString { it.projectile.javaClass.simpleName }}")
        castHand(caster, hand)
        discard()
    }

    /** 释放手牌中的每个法术链。 */
    private fun castHand(caster: Entity, hand: List<SpellChain>) {
        for (chain in hand) {
            val allEffects = arrayListOf<BaseEffect>()
            for (mod in chain.modifiers) {
                mod.caster = caster
                mod.wand = this
                mod.onSpell()
                allEffects.addAll(mod.effects)
            }

            val projectile = chain.projectile.clone()
            projectile.effects.addAll(0, allEffects)
            projectile.spell()
            MagicManager.add(projectile)
        }
    }

    private fun discard() {
        discardPile += hand
        hand.clear()
        reshuffleIfNeeded()
    }

    fun draw(budget: Int): Int {
        var remaining = budget
        var drawn = 0
        while (remaining > 0 && drawPile.isNotEmpty()) {
            val chain = drawPile.removeFirst()
            hand += chain
            remaining -= chain.drawCost
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
