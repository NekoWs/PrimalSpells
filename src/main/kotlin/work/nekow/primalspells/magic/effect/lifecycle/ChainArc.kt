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
        if (caster.level() !== world || jumps >= 3 || status.age >= 60) { projectile.alive = false; return }
        if (cooldown-- > 0) return
        val candidates = world.getEntitiesOfClass(LivingEntity::class.java,
            net.minecraft.world.phys.AABB.ofSize(start, 10.0, 10.0, 10.0)) {
            it.isAlive && it !== caster && !caster.isAlliedTo(it) && it.uuid !in visited &&
                it.boundingBox.center.distanceToSqr(start) <= 25.0
        }.sortedWith(compareBy<LivingEntity> { it.boundingBox.center.distanceToSqr(start) }.thenBy { it.id })
        val target = candidates.firstOrNull {
            world.clip(ClipContext(start, it.boundingBox.center, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).type == net.minecraft.world.phys.HitResult.Type.MISS
        } ?: run { projectile.alive = false; return }
        val end = target.boundingBox.center
        val manager = ParticleManager.of(world)
        val group = manager.createGroup(start)
        // 固定端点折线：形状由本侧给出，一次性闪电寿命/淡出完全由 PD 负责。
        val points = (0..12).map { i ->
            val p = start.lerp(end, i / 12.0)
            if (i == 0 || i == 12) p else p.add(0.0, if (i % 2 == 0) 0.13 else -0.13, 0.0)
        }
        Draw.curve(manager, { t ->
            val step = t * (points.size - 1)
            val index = step.toInt().coerceAtMost(points.size - 2)
            points[index].lerp(points[index + 1], step - index)
        }, 160, ColorSource.of(Color.ofInt(120, 210, 255, 255)), 1.2f, group = group,
            visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false))
        group.scale(0.05f, 8, work.nekow.particledrawing.core.easing.EasingType.LINEAR).destroyAfter(8)
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
