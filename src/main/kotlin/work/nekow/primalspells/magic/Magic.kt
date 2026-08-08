package work.nekow.primalspells.magic

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.effect.BaseEffect
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
    var maxAge: Int = 0
    var alive: Boolean = true

    open fun spell() {
        effects.forEach {
            it.caster = caster
            it.wand = wand
            it.onActive()
        }
        onSpell()
    }

    fun hitEntity(target: Entity) {
        effects.forEach { it.onHitEntity(target) }
    }

    fun hitBlock(pos: Vector3f) {
        effects.forEach { it.onHitBlock(pos) }
    }
    fun tick() {
        effects.forEach { it.onTick() }
    }
    open fun onSpell() { }

    /** 将当前法术的属性复制到目标。先清空 targets 的 effects 再复制，避免与 init {} 重复。 */
    protected fun copyTo(target: Magic) {
        target.cast = cast
        target.mana = mana
        target.delay = delay
        target.recharge = recharge
        target.maxAge = maxAge
        target.effects.clear()
        target.effects += effects
//        target.caster = caster
//        target.wand = wand
        if (this is TriggerSpell && target is TriggerSpell) {
            target.payload = this.payload
        }
    }

    abstract fun clone(): Magic

    companion object {
        private val registry = mutableMapOf<String, () -> Magic>()

        fun register(id: String, factory: () -> Magic) {
            registry[id] = factory
        }

        fun create(id: String): Magic? = registry[id]?.invoke()
    }
}