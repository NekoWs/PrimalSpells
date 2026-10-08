package work.nekow.primalspells.magic.core

import work.nekow.primalspells.magic.projectile.Projectile
import kotlin.math.pow

/** 本次施法的修饰配方；继承时重建效果实例，不复制运行中effect或重复乘已结算的数值。 */
object SpellAssembly {
    private val generators = setOf("bounce_duplicate", "explosion_projectile", "quantum_entanglement", "double_cast")
    private val motion = setOf("speed_boost", "gradual_accelerate", "gradual_decelerate", "gravity", "homing",
        "bounce", "bounce_duplicate", "random_path", "reciprocate", "sine_wave", "self_orbit", "sphere_orbit")

    /** 注入独立effect并保存id计数，供视觉和后代继承使用。 */
    fun apply(shot: Projectile, modifiers: List<Modifier>) {
        modifiers.forEach { modifier ->
            shot.modifierOrder += modifier.id
            shot.modifiers[modifier.id] = (shot.modifiers[modifier.id] ?: 0) + 1
            shot.effects += modifier.effects()
        }
    }

    fun inheritable(shot: Projectile): Map<String, Int> = shot.modifiers.filterKeys { it !in generators }

    /** 保留抽牌顺序；加法伤害与乘法伤害不能交换次序。 */
    fun inheritableOrder(shot: Projectile): List<String> = shot.modifierOrder.filter { it !in generators }

    /** 复制/集束/定时发射/触发荷载继承非生成型修饰器，避免子弹自我繁殖的无穷递归。 */
    fun inherit(parent: Projectile, child: Projectile) {
        val mods = inheritableOrder(parent).map { MagicManager.create(it) as Modifier }
        apply(child, mods)
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
    }
}
