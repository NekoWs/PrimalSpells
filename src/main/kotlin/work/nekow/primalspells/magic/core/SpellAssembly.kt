package work.nekow.primalspells.magic.core

import work.nekow.primalspells.magic.projectile.Projectile
import kotlin.math.pow

/** 本次施法的修饰配方；继承时重建效果实例，不复制运行中effect或重复乘已结算的数值。 */
object SpellAssembly {
    private val generators = setOf("bounce_duplicate", "explosion_projectile", "quantum_entanglement", "double_cast")
    private val motion = setOf("speed_boost", "gradual_accelerate", "gradual_decelerate", "gravity", "homing",
        "bounce", "bounce_duplicate", "random_path", "reciprocate", "sine_wave", "self_orbit")

    /** 注入独立effect并保存id计数，供视觉和后代继承使用。 */
    fun apply(shot: Projectile, modifiers: List<Modifier>) {
        modifiers.sortedBy { it.id }.forEach { modifier ->
            shot.modifierOrder += modifier.id
            shot.modifiers[modifier.id] = (shot.modifiers[modifier.id] ?: 0) + 1
            shot.effects += modifier.effects()
        }
    }

    fun inheritable(shot: Projectile): Map<String, Int> = shot.modifiers.filterKeys { it !in generators }

    /** 保存规范化配方，卡牌排列不改变同一链的效果结算。 */
    fun inheritableOrder(shot: Projectile): List<String> = shot.modifierOrder.filter { it !in generators }

    /** 同源复制体继承非生成型修饰器；独立触发荷载不调用此入口。 */
    fun inherit(parent: Projectile, child: Projectile) {
        val mods = inheritableOrder(parent).map { MagicManager.create(it) as Modifier }
        apply(child, mods)
    }

    /** 同源复制保留独立荷载树；不共享可变实例，也不重新抽牌或扣蓝。 */
    fun copy(parent: Projectile): Projectile = parent.clone().also { child ->
        inherit(parent, child)
        copyPayload(parent, child)
        copyAppended(parent, child)
    }

    /** 深拷贝尚未发射的抽牌树，不继承运行时状态。 */
    fun copyCard(card: Magic): Magic = card.clone().also { copied ->
        // 追加系列的免费修正属于准备态费用，复制后仍然免费。
        copied.mana = card.mana
        copyPayload(card, copied)
        if (card is DrawAction && copied is DrawAction) copied.expanded += card.expanded.map(::copyCard)
        if (card is Projectile && copied is Projectile) copyAppended(card, copied)
    }

    private fun copyAppended(parent: Projectile, child: Projectile) {
        parent.addedTriggers.forEach { child.addedTriggers += AppendedPayload(it.kind, ArrayList(it.cards.map(::copyCard))) }
    }

    private fun copyPayload(parent: Magic, child: Magic) {
        if (parent !is TriggerSpell || child !is TriggerSpell) return
        parent.payload.forEach { card ->
            val copied = copyCard(card)
            if (card is Projectile && copied is Projectile) {
                apply(copied, card.modifierOrder.map { MagicManager.create(it) as Modifier })
            }
            child.payload += copied
        }
    }

    /** 子弹按当前速度发射，抵消基础speed及速度卡的重复乘法，再交由SETUP正常装配。 */
    fun launch(child: Projectile, direction: org.joml.Vector3f, speed: Float): org.joml.Vector3f {
        if (child.speed == 0f) {
            child.speed = if (child.modifiers.keys.any { it in motion }) 0.25f else 1f
            child.maxAge = child.maxAge.coerceAtLeast(40)
        }
        val boosts = 2.0.pow(child.modifiers["speed_boost"] ?: 0).toFloat()
        val heading = org.joml.Vector3f(direction)
        if (heading.lengthSquared() < 1e-8f) heading.set(0f, 0f, 1f)
        return heading.normalize().mul(speed / (child.speed * boosts * Projectile.LAUNCH_SPEED_SCALE))
    }

    /** 引爆卡把自己的非生成型配方传给已布置晶体，立即激活独立effect。 */
    fun reinforce(parent: Projectile, child: Projectile) {
        val begin = child.effects.size
        inherit(parent, child)
        child.areaScale = 2.0.pow((child.modifiers["diffusion"] ?: 0) - (child.modifiers["implosion"] ?: 0))
        child.effects.drop(begin).sortedBy { it.phase.activeOrder }.forEach {
            it.projectile = child; it.status = child.status; it.caster = child.caster; it.wand = child.wand
            it.onActive()
        }
    }

    /** 静止法术装上运动卡后成为可飞场源/炸弹；基础静止施法保持定点。 */
    fun prepare(shot: Projectile) {
        if (shot.speed == 0f && shot.modifiers.keys.any { it in motion }) {
            shot.speed = 0.25f
            if (shot.maxAge <= 1) shot.maxAge = 40
        }
        if ("explosion_projectile" in shot.modifiers && shot.maxAge <= 1) shot.maxAge = 40
        shot.areaScale = 2.0.pow((shot.modifiers["diffusion"] ?: 0) - (shot.modifiers["implosion"] ?: 0))
        shot.hitRadius *= shot.areaScale
        if ("volatile" in shot.modifiers && shot.effects.none { it is work.nekow.primalspells.magic.effect.lifecycle.Explosion || it is work.nekow.primalspells.magic.effect.lifecycle.CrystalReaction })
            shot.effects += work.nekow.primalspells.magic.effect.lifecycle.Explosion(onHit = true, onDeath = true)
        if ("piercing" in shot.modifiers || shot.effects.any {
            it is work.nekow.primalspells.magic.effect.lifecycle.Explosion || it is work.nekow.primalspells.magic.effect.lifecycle.CrystalReaction
        }) shot.selfDamage = true
    }
}
