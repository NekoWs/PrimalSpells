package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import java.util.UUID

/** 电弧命中后至多跳向三名新目标；逐跳衰减、有墙遮挡，命中集合每次施法独立持有。 */
class ChainArc : BaseEffect(), DirectDamage {
    override val phase = EffectPhase.OVERRIDE
    override val damageStrength: Double get() = status.damage
    private val visited = HashSet<UUID>()
    private var level: ServerLevel? = null
    private var last: Vec3? = null
    private var cooldown = 0
    private var jumps = 0
    override fun onActive() { level = caster.level() as? ServerLevel }
    override fun onHitEntity(target: Entity) {
        if (last != null || target !is LivingEntity) return
        val world = level ?: return
        if (!SpellDamage.hit(world, target, projectile, status.damage)) return
        visited += target.uuid
        last = target.boundingBox.center
        projectile.alive = true
        projectile.motionEnabled = false
        status.velocity.zero()
        status.pos.set(last!!.x.toFloat(), last!!.y.toFloat(), last!!.z.toFloat())
        cooldown = 4
    }
    override fun onTick() {
        val world = level ?: return
        val start = last ?: return
        if (!projectile.alive) return
        if (caster.level() !== world || jumps >= 3 + (projectile.modifiers["bounce"] ?: 0) * 2 || status.age >= projectile.maxAge) { projectile.alive = false; return }
        if (cooldown-- > 0) return
        val candidates = world.getEntitiesOfClass(LivingEntity::class.java,
            net.minecraft.world.phys.AABB.ofSize(start, 10.0 * projectile.areaScale, 10.0 * projectile.areaScale, 10.0 * projectile.areaScale)) {
            it.isAlive && it !== caster && !caster.isAlliedTo(it) && it.uuid !in visited &&
                it.boundingBox.center.distanceToSqr(start) <= 25.0 * projectile.areaScale * projectile.areaScale
        }.sortedWith(compareBy<LivingEntity> { it.boundingBox.center.distanceToSqr(start) }.thenBy { it.id })
        val target = candidates.firstOrNull {
            world.clip(ClipContext(start, it.boundingBox.center, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).type == net.minecraft.world.phys.HitResult.Type.MISS
        } ?: run { projectile.alive = false; return }
        val end = target.boundingBox.center
        work.nekow.primalspells.magic.effect.visual.PixelVfx.arc(ParticleManager.of(world), start, end)
        SpellInteractions.beam(projectile, start, end, work.nekow.primalspells.magic.core.MagicManager.projectiles.toList())
        status.damage *= 0.7
        visited += target.uuid
        jumps++
        last = end
        status.pos.set(end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
        // 临时线段速度让护盾按真实来向判断；随后停止积分，不把跳跃误当成自由飞行。
        val delta = end.subtract(start)
        status.velocity.set(delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())
        val accepted = SpellDamage.hit(world, target, projectile, status.damage)
        if (!accepted) { projectile.alive = false; return }
        status.velocity.zero()
        cooldown = 4
    }
}
