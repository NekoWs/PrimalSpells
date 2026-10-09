package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/** 移动束源发出六格持续光束；最终位置求值、碰墙截断，每 tick 结算且最多选四个目标。 */
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
        if (caster.level() !== world || status.age >= projectile.maxAge) { projectile.alive = false; return }
        val start = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        if (status.velocity.lengthSquared() > 1e-8f)
            direction = Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble()).normalize()
        val intended = start.add(direction.scale(6.0 * projectile.areaScale))
        val hit = world.clip(ClipContext(start, intended, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
        val end = if (projectile.maxHitTargets < 0 || hit.type == net.minecraft.world.phys.HitResult.Type.MISS) intended else hit.location
        val beam = group ?: createBeam(world, start, end).also { group = it }
        for ((name, value) in listOf("sx" to start.x, "sy" to start.y, "sz" to start.z,
            "ex" to end.x, "ey" to end.y, "ez" to end.z)) beam.setVariableInterpolated(name, value.toString(), 1)
        if (status.age % 4 == 0) {
            SpellInteractions.beam(projectile, start, end, work.nekow.primalspells.magic.core.MagicManager.projectiles.toList())
            work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(ParticleManager.of(world), end,
                Color.ofInt(120, 227, 255), 32, 0.12, 8, 0.006)
        }
        if (start.distanceToSqr(end) < 1e-8) return
        val candidates = world.getEntitiesOfClass(LivingEntity::class.java, AABB(start, end).inflate(0.25 * projectile.areaScale)) {
            it.isAlive && projectile.canHarm(it) && (it === caster || !caster.isAlliedTo(it)) &&
                it.boundingBox.inflate(0.15 * projectile.areaScale).clip(start, end).isPresent
        }.sortedBy { it.boundingBox.center.distanceToSqr(start) }.take(if (projectile.maxHitTargets < 0) Int.MAX_VALUE else 4)
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
        for (layer in 0..2) {
            val color = if (layer == 0) Color.ofInt(225, 252, 255) else Color.ofInt(54, 185, 242)
            Draw.curve(manager, { t -> start.lerp(end, t) }, 192, ColorSource.of(color),
                if (layer == 0) 0.7f else 0.45f, group = beam,
                visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))
        }
        for ((name, value) in listOf("sx" to start.x, "sy" to start.y, "sz" to start.z,
            "ex" to end.x, "ey" to end.y, "ez" to end.z)) beam.setVariable(name, value)
        beam.setVariable("endMs", 1.0e9)
        // 实色方点拼出像素束；双层螺旋与退场径向散开，透明度始终为1。
        beam.expression("u=(i%192)/191;layer=floor(i/192);dx=ex-sx;dz=ez-sz;ll=max(0.001,sqrt(dx*dx+dz*dz));amp=layer*0.06;ang=u*36-t*0.045;out=min(1,max(0,(t-endMs)/300));out=out*out*(3-2*out);birth=min(1,t/100);birth=birth*birth*(3-2*birth);off=amp+out*0.3;x=sx+(ex-sx)*u-dz/ll*off*cos(ang);y=sy+(ey-sy)*u+off*sin(ang);z=sz+(ez-sz)*u+dx/ll*off*cos(ang);a=1;sc=birth*(1-out);")

        return beam
    }
    private fun worldBurst() {
        val world = level ?: return
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(ParticleManager.of(world),
            Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()), Color.ofInt(102, 209, 247), 64, 0.14, 14)
    }
    override fun onDie() {
        group?.setVariableLive("endMs", ((status.age - 1).coerceAtLeast(0) * 50).toString())
        // 表达式收尾尚无PD完成回报；保留有限余量，见第72轮API需求。
        group?.destroyAfter(8)
        worldBurst()
        group = null
    }
}
