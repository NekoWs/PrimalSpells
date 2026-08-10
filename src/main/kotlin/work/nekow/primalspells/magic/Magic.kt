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

    open var cast: Int = 1

    var mana: Double = 0.0
    var delay: Int = 0
    var recharge: Int = 0
    var effects = arrayListOf<BaseEffect>()
    var lore = arrayListOf<LoreEntry>()

    /**
     * 快捷添加提示
     *
     * @param lore 提示类型
     * @param args 参数
     */
    fun lore(lore: Lore, vararg args: Any) {
        this.lore += LoreEntry(lore, args)
    }

    open fun spell() {
        effects.reversed().forEach {
            it.caster = caster
            it.wand = wand
            it.onActive()
        }
        onSpell()
    }

    fun tick() {
        effects.forEach { it.onTick() }
    }

    fun die() {
        effects.forEach { it.onDie() }
        onDie()
    }

    open fun onSpell() { }
    open fun onDie() { }

    open fun initLore() {
        val base = arrayListOf<LoreEntry>()
        base += LoreEntry(Lore.DESCRIPTION, emptyArray())
        base += LoreEntry(Lore.BR, emptyArray())
        base += LoreEntry(Lore.MANA, arrayOf(Lore.formatDouble(mana)))
        if (delay > 0) base += LoreEntry(Lore.DELAY, arrayOf(delay.toString()))
        if (recharge > 0) base += LoreEntry(Lore.RECHARGE, arrayOf(recharge.toString()))
        lore.addAll(0, base)
    }

    open fun clone(): Magic = MagicManager.create(id)!!
}