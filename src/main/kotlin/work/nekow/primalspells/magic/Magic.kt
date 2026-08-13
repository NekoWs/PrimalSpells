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

    /** 消耗法术值 **/
    var mana: Double = 0.0
    /** 增加施法延迟 **/
    var delay: Int = 0
    /** 增加充能时间 **/
    var recharge: Int = 0
    var effects = arrayListOf<BaseEffect>()
    var lore = arrayListOf<LoreEntry>()

    /** 被动开关：为 true 时，法杖持于玩家手上（主手/副手）期间每 tick 触发 [onInventoryTick] */
    open var needInventory: Boolean = false

    /**
     * 被动效果钩子：仅在 [needInventory] 为 true、且该法术已编入法杖、法杖位于玩家手上时，
     * 由 [Wand.tick] 每 tick 调用一次（调用前已设置好 [caster]/[wand]）。
     */
    open fun onInventoryTick() { }

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
        if (delay != 0) base += LoreEntry(Lore.DELAY, arrayOf(delay.toString()))
        if (recharge != 0) base += LoreEntry(Lore.RECHARGE, arrayOf(recharge.toString()))
        lore.addAll(0, base)
    }

    open fun clone(): Magic = MagicManager.create(id)!!
}