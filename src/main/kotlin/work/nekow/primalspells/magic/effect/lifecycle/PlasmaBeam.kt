package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/** 移动束源发出六格持续光束；最终位置求值、碰墙截断，每五 tick 结算且最多选四个目标。 */
class PlasmaBeam : BaseEffect(), DirectDamage {
    override val phase = EffectPhase.VISUAL
    override val damageStrength: Double get() = status.damage
    private var level: ServerLevel? = null
    private var direction = Vec3.ZERO
    private var group: ParticleGroup? = null
    override fun onActive() {
        level = caster.level() as? ServerLevel
        direction = if (status.velocity.lengthSquared() > 1e-8f)
            Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble()).normalize()
            else projectile.launchDirection
    }
    override fun onTick() {
        val world = level ?: return
        if (!projectile.alive) return
        if (caster.level() !== world || status.age >= 40) { projectile.alive = false; return }
        val start = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        if (status.velocity.lengthSquared() > 1e-8f)
            direction = Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble()).normalize()
        val intended = start.add(direction.scale(6.0))
        val hit = world.clip(ClipContext(start, intended, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
        val end = if (hit.type == net.minecraft.world.phys.HitResult.Type.MISS) intended else hit.location
        val beam = group ?: createBeam(world, start, end).also { group = it }
        for ((name, value) in listOf("sx" to start.x, "sy" to start.y, "sz" to start.z,
            "ex" to end.x, "ey" to end.y, "ez" to end.z)) beam.setVariableLive(name, value.toString())
        if (status.age % 5 != 0 || start.distanceToSqr(end) < 1e-8) return
        val candidates = world.getEntitiesOfClass(LivingEntity::class.java, AABB(start, end).inflate(0.25)) {
            it.isAlive && it !== caster && !caster.isAlliedTo(it) &&
                it.boundingBox.inflate(0.15).clip(start, end).isPresent
        }.sortedBy { it.boundingBox.center.distanceToSqr(start) }.take(4)
        val originalPos = org.joml.Vector3f(status.pos)
        val originalVelocity = org.joml.Vector3f(status.velocity)
        try {
            for (target in candidates) {
                val point = target.boundingBox.inflate(0.15).clip(start, end).orElse(target.boundingBox.center)
                status.pos.set(point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                val delta = point.subtract(start)
                status.velocity.set(delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())
                if (!SpellDamage.hit(world, target, projectile, status.damage)) break
            }
        } finally { status.pos.set(originalPos); status.velocity.set(originalVelocity) }
    }
    private fun createBeam(world: ServerLevel, start: Vec3, end: Vec3): ParticleGroup {
        val manager = ParticleManager.of(world)
        val beam = manager.createGroup(start)
        Draw.curve(manager, { t -> start.lerp(end, t) }, 120, ColorSource.of(Color.ofInt(76, 216, 255, 240)),
            2.0f, group = beam, visual = ParticleVisual().style(ParticleStyle.SOFT_DOT).glowing(true).additive(true))
        for ((name, value) in listOf("sx" to start.x, "sy" to start.y, "sz" to start.z,
            "ex" to end.x, "ey" to end.y, "ez" to end.z)) beam.setVariable(name, value)
        beam.expression("u=i/max(1,n-1);x=sx+(ex-sx)*u;y=sy+(ey-sy)*u;z=sz+(ez-sz)*u;r=0.3;g=0.85;b=1;a=0.7+0.2*sin(t*0.04+u*16);sc=0.85+0.15*sin(t*0.06+u*20);")
        beam.fadeIn(3)
        return beam
    }
    override fun onDie() { group?.fadeOut(10, removeAfter = true, easing = EasingType.LINEAR); group = null }
}
