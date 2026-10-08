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

/** 电弧命中后按剩余寿命逐跳索敌，速度决定索敌半径；逐跳衰减、有墙遮挡，命中集合每次施法独立持有。 */
class ChainArc : BaseEffect(), DirectDamage {
    override val phase = EffectPhase.OVERRIDE
    override val damageStrength: Double get() = status.damage
    private val visited = HashSet<UUID>()
    private var level: ServerLevel? = null
    private var last: Vec3? = null
    private var cooldown = 0
    private var searchSpeed = 0.0
    override fun onActive() { level = caster.level() as? ServerLevel }
    override fun onHitEntity(target: Entity) {
        if (last != null || target !is LivingEntity) return
        val world = level ?: return
        if (!SpellDamage.hit(world, target, projectile, status.damage)) return
        searchSpeed = status.velocity.length().toDouble()
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
        if (caster.level() !== world || status.age >= projectile.maxAge) { projectile.alive = false; return }
        if (cooldown-- > 0) return
        val radius = (searchSpeed * 4.0).coerceAtLeast(1.0) * projectile.areaScale
        val candidates = world.getEntitiesOfClass(LivingEntity::class.java,
            net.minecraft.world.phys.AABB.ofSize(start, radius * 2, radius * 2, radius * 2)) {
            it.isAlive && it !== caster && !caster.isAlliedTo(it) && it.uuid !in visited &&
                it.boundingBox.center.distanceToSqr(start) <= radius * radius
        }.sortedWith(compareBy<LivingEntity> { it.boundingBox.center.distanceToSqr(start) }.thenBy { it.id })
        val target = candidates.firstOrNull {
            world.clip(ClipContext(start, it.boundingBox.center, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).type == net.minecraft.world.phys.HitResult.Type.MISS
        } ?: run { cooldown = 4; return }
        val end = target.boundingBox.center
        work.nekow.primalspells.magic.effect.visual.PixelVfx.arc(ParticleManager.of(world), start, end)
        SpellInteractions.beam(projectile, start, end, work.nekow.primalspells.magic.core.MagicManager.projectiles.toList())
        status.damage *= 0.7
        visited += target.uuid
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
