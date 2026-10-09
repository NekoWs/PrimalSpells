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
    private var source: ParticleGroup? = null
    /** 本刻可见光束的实际终点，用于交互与开发验收。 */
    var beamEnd: Vec3 = Vec3.ZERO
        private set
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
        var end = if (projectile.maxHitTargets < 0 || hit.type == net.minecraft.world.phys.HitResult.Type.MISS) intended else hit.location
        // 束源始终留在原处；先让完整射线通过正式护盾入口，再绘制实际可见区间。
        val originalPos = org.joml.Vector3f(status.pos)
        val originalVelocity = org.joml.Vector3f(status.velocity)
        var blocked = false
        try {
            if (projectile.maxHitTargets >= 0 && start.distanceToSqr(end) > 1e-8) {
                val from = start.toVector3f()
                val to = end.toVector3f()
                status.velocity.set(to).sub(from)
                val shields = world.getEntitiesOfClass(LivingEntity::class.java, AABB(start, end).inflate(2.7)) {
                    it.isAlive && work.nekow.primalspells.magic.core.ShieldGuard.hasShield(it)
                }.mapNotNull { holder -> work.nekow.primalspells.magic.core.ShieldGuard.segmentEntry(holder, from, to)
                    ?.let { holder to it.distanceToSqr(start) } }.sortedBy { it.second }
                for ((holder, _) in shields) {
                    if (work.nekow.primalspells.magic.core.ShieldGuard.interceptSegment(world, holder, projectile, from, to)) {
                        end = projectile.shieldReflectionPoint ?: end
                        blocked = true
                        break
                    }
                }
            }
        } finally { status.pos.set(originalPos); status.velocity.set(originalVelocity) }
        beamEnd = end
        val beam = group ?: createBeam(world, start, end).also { group = it }
        val core = source ?: createSource(world, start).also { source = it }
        for ((name, value) in listOf("sx" to start.x, "sy" to start.y, "sz" to start.z,
            "ex" to end.x, "ey" to end.y, "ez" to end.z)) {
            // 抵挡当刻直接发布截点，不能从旧端点再穿盾缓动一刻。
            if (blocked && name.startsWith("e")) beam.setVariableLive(name, value.toString())
            else beam.setVariableInterpolated(name, value.toString(), 1)
            if (name.startsWith("s")) core.setVariableInterpolated(name, value.toString(), 1)
        }
        if (status.age % 4 == 0) {
            SpellInteractions.beam(projectile, start, end, work.nekow.primalspells.magic.core.MagicManager.projectiles.toList())
            work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(ParticleManager.of(world), end,
                Color.ofInt(120, 227, 255), if (blocked) 8 else 32, 0.12, 8, 0.006)
        }
        if (start.distanceToSqr(end) < 1e-8) return
        val candidates = world.getEntitiesOfClass(LivingEntity::class.java, AABB(start, end).inflate(0.25 * projectile.areaScale)) {
            it.isAlive && projectile.canHarm(it) && (it === caster || !caster.isAlliedTo(it)) &&
                (it.boundingBox.inflate(0.15 * projectile.areaScale).contains(start) ||
                    it.boundingBox.inflate(0.15 * projectile.areaScale).clip(start, end).isPresent)
        }.sortedBy { it.boundingBox.center.distanceToSqr(start) }.take(if (projectile.maxHitTargets < 0) Int.MAX_VALUE else 4)
        try {
            for (target in candidates) {
                val box = target.boundingBox.inflate(0.15 * projectile.areaScale)
                val point = if (box.contains(start)) start else box.clip(start, end).orElse(target.boundingBox.center)
                status.pos.set(point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                val delta = point.subtract(start)
                status.velocity.set(delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())
                SpellDamage.hit(world, target, projectile, status.damage)
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
        beam.setVariable("presence", 1.0)
        // 实色方点拼出像素束；双层螺旋与退场径向散开，透明度始终为1。
        beam.expression("u=(i%192)/191;layer=floor(i/192);dx=ex-sx;dz=ez-sz;ll=max(0.001,sqrt(dx*dx+dz*dz));amp=layer*0.06;ang=u*36-t*0.045;out=1-presence;birth=min(1,t/100);birth=birth*birth*(3-2*birth);off=amp+out*0.3;x=sx+(ex-sx)*u-dz/ll*off*cos(ang);y=sy+(ey-sy)*u+off*sin(ang);z=sz+(ez-sz)*u+dx/ll*off*cos(ang);a=1;sc=birth*(1-out);")

        return beam
    }
    /** 实色晶格束源：双层对转的晶格，束首热点明确标记光束起点。 */
    private fun createSource(world: ServerLevel, start: Vec3): ParticleGroup {
        val manager = ParticleManager.of(world)
        val core = manager.createGroup(start)
        repeat(40) { i ->
            core.add(manager.create().position(start).color(if (i < 8) Color.ofInt(225,252,255) else Color.ofInt(45,180,235))
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.1f,0.1f).glowing(true))
                .lifetime(-1).spawn())
        }
        core.setVariable("sx",start.x).setVariable("sy",start.y).setVariable("sz",start.z).setVariable("presence",1.0)
            .expression("layer=floor(i/20);q=(i%20)/20;ang=q*2*PI+t*0.0015*(1-2*layer);rad=0.22*(1-0.25*layer);birth=min(1,t/100);x=sx+rad*cos(ang);y=sy+rad*sin(ang)*(1-layer);z=sz+rad*sin(ang)*layer;a=1;sc=birth*presence;")
        return core
    }

    private fun worldBurst() {
        val world = level ?: return
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(ParticleManager.of(world),
            Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()), Color.ofInt(102, 209, 247), 64, 0.14, 14)
    }
    override fun onDie() {
        // 按PD客户端确认的变量终点回收，保留默认两刻桥接余量。
        group?.setVariableInterpolated("presence", "0", 6, work.nekow.particledrawing.core.easing.EasingType.EASE_IN_OUT)?.retire()
        source?.setVariableInterpolated("presence", "0", 6, work.nekow.particledrawing.core.easing.EasingType.EASE_IN_OUT)?.retire()
        source = null
        worldBurst()
        group = null
    }
}
