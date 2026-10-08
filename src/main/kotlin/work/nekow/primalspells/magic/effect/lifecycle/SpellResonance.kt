package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.visual.PixelVfx
import work.nekow.primalspells.util.HitResult

/** 通用弹体质感与修饰共鸣；服务端维护玩法，PD负责冲击和标记动画。 */
class SpellResonance : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var world: ServerLevel? = null
    private var lastHitAge = -20
    private fun pos() = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
    override fun onActive() { world = caster.level() as? ServerLevel }
    override fun onTick() {
        val level = world ?: return
        if (!projectile.alive) return
        val manager = ParticleManager.of(level)
        val center = pos()
        val count = projectile.modifiers.values.sum()
        if (projectile.id !in setOf("laser", "plasma_beam", "chain_bolt", "delayed_cast", "chainsaw") && status.age % 4 == 0 && (count > 0 || status.velocity.lengthSquared() > 0.01f)) {
            PixelVfx.burst(manager, center, tint(), 8 + count * 3, 0.045, 4, 0.0)
        }
        // 护盾卡也能包裹发射出去的法术：真实反射敌方魔法弹，归属改为当前施法者。
        if ("energy_shield" in projectile.modifiers) {
            for (other in MagicManager.projectiles.toList()) {
                if (other === projectile || !other.alive || other.caster === caster || other.caster.level() !== level) continue
                val p = Vec3(other.status.pos.x.toDouble(), other.status.pos.y.toDouble(), other.status.pos.z.toDouble())
                if (p.distanceToSqr(center) > 1.0) continue
                val normal = p.subtract(center).normalize()
                val velocity = Vec3(other.status.velocity.x.toDouble(), other.status.velocity.y.toDouble(), other.status.velocity.z.toDouble())
                if (velocity.dot(normal) >= 0) continue
                val reflected = velocity.subtract(normal.scale(2 * velocity.dot(normal)))
                other.status.velocity.set(reflected.x.toFloat(), reflected.y.toFloat(), reflected.z.toFloat())
                other.caster = caster
                other.effects.forEach { it.caster = caster }
                PixelVfx.ring(manager, p, normal, 0.25, Color.ofInt(104, 204, 255), 10, 3f)
            }
        }
        // 场源/召唤源也接受伤害与冻结卡；原本无直接攻击的源获得近距离共鸣脉冲。
        if (status.age % 10 == 0 && projectile.effects.none { it is Hurt || it is DirectDamage || it is Explosion }
            && ("damage_boost" in projectile.modifiers || status.freezeTicks > 0)) {
            level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java,
                net.minecraft.world.phys.AABB.ofSize(center, 3.0 * projectile.areaScale, 3.0 * projectile.areaScale, 3.0 * projectile.areaScale)) {
                it !== caster && !caster.isAlliedTo(it)
            }.forEach { SpellDamage.hit(level, it, projectile, status.damage.coerceAtLeast(1.0)) }
        }
    }
    override fun onHit(result: HitResult) {
        if (status.age - lastHitAge < 3) return
        lastHitAge = status.age
        val level = world ?: return
        val p = Vec3(result.pos.x.toDouble(), result.pos.y.toDouble(), result.pos.z.toDouble())
        if (result.entity != null && projectile.effects.none { it is Hurt || it is DirectDamage || it is Explosion }
            && ("damage_boost" in projectile.modifiers || status.freezeTicks > 0))
            SpellDamage.hit(level, result.entity, projectile, status.damage.coerceAtLeast(1.0))
        PixelVfx.burst(ParticleManager.of(level), p, tint(), if (projectile.id == "laser") 16 else 48 + projectile.modifiers.values.sum() * 12, if (projectile.id == "laser") 0.1 else 0.22, if (projectile.id == "laser") 5 else 12)
    }
    private fun tint(): Color = when {
        status.freezeTicks > 0 -> Color.ofInt(128, 222, 255)
        "mana_injection" in projectile.modifiers -> Color.ofInt(102, 240, 154)
        "volatile" in projectile.modifiers -> Color.ofInt(255, 154, 60)
        "energy_shield" in projectile.modifiers -> Color.ofInt(100, 195, 255)
        else -> projectile.bodyColor
    }
}
