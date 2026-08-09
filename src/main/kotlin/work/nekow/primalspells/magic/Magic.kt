package work.nekow.primalspells.magic

import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore
import work.nekow.primalspells.utils.LoreEntry
import work.nekow.primalspells.wand.Wand

abstract class Magic {
    abstract val id: String

    lateinit var caster: Entity
    lateinit var wand: Wand

    /**
     * 法术提供的抽卡次数
     */
    open var cast: Int = 1

    var mana: Double = 0.0
    var delay: Int = 0
    var recharge: Int = 0
    var effects = arrayListOf<BaseEffect>()
    var lore = arrayListOf<LoreEntry>()

    open fun spell() {
        effects.forEach {
            it.caster = caster
            it.wand = wand
            it.onActive()
        }
        onSpell()
    }

    fun tick() {
        effects.forEach { it.onTick() }
    }
    open fun onSpell() { }

    open fun initLore() {
        if (mana > 0) lore += LoreEntry(Lore.MANA, arrayOf(Lore.formatDouble(mana)))
        if (delay > 0) lore += LoreEntry(Lore.DELAY, arrayOf(delay.toString()))
        if (recharge > 0) lore += LoreEntry(Lore.RECHARGE, arrayOf(recharge.toString()))
    }

    open fun clone(): Magic = MagicManager.create(id)!!
}