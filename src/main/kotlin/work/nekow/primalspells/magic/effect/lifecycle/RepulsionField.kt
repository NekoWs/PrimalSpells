package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 斥力场：半径四格，对魔法弹与原版弹施加有限外推；不反射归属，不影响生物。 */
class RepulsionField : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var level: ServerLevel? = null
    private var group: ParticleGroup? = null
    private var lastCenter: Vec3? = null
    override fun onActive() { level = caster.level() as? ServerLevel }
    override fun onTick() {
        val world = level ?: return
        if (!projectile.alive) return
        if (caster.level() !== world) { projectile.alive = false; return }
        val center = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        if (group == null) {
            val manager = ParticleManager.of(world)
            val shell = manager.createGroup(center)
            repeat(3) { plane ->
                Draw.curve(manager, { t ->
                    val a = t * PI * 2; val u = 4 * projectile.areaScale * cos(a); val v = 4 * projectile.areaScale * sin(a)
                    center.add(if (plane == 2) 0.0 else u, if (plane == 0) 0.0 else v,
                        if (plane == 0) v else if (plane == 2) u else 0.0)
                }, 96, ColorSource.of(Color.ofInt(245, 145, 80, 255)), 0.6f, group = shell,
                    visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false))
            }
            shell.spin(Vec3(0.2, 1.0, 0.1).normalize(), 0.025).pulse(1.04f, 10)
            group = shell
        }
        if (lastCenter != center) group?.setPivot(center)
        lastCenter = center
        if (status.age % 8 == 0) work.nekow.primalspells.magic.effect.visual.PixelVfx.ring(ParticleManager.of(world),
            center, Vec3(0.0, 1.0, 0.0), 0.75 * projectile.areaScale, Color.ofInt(245, 145, 80), 10, 5f)
        for (p in MagicManager.projectiles) {
            if (p === projectile || !p.alive || !p.motionEnabled || p.caster.level() !== world) continue
            val offset = Vec3(p.status.pos.x.toDouble(), p.status.pos.y.toDouble(), p.status.pos.z.toDouble()).subtract(center)
            if (offset.lengthSqr() !in 1e-6..(16.0 * projectile.areaScale * projectile.areaScale)) continue
            val push = offset.normalize().scale(0.12 * kotlin.math.sqrt(1 + projectile.storedEnergy))
            p.status.velocity.add(push.x.toFloat(), push.y.toFloat(), push.z.toFloat())
            if (p.status.velocity.length() > 4f) p.status.velocity.normalize().mul(4f)
        }
        for (p in world.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile::class.java,
            AABB.ofSize(center, 8.0 * projectile.areaScale, 8.0 * projectile.areaScale, 8.0 * projectile.areaScale)) { it.isAlive }) {
            val offset = p.position().subtract(center)
            if (offset.lengthSqr() !in 1e-6..(16.0 * projectile.areaScale * projectile.areaScale)) continue
            val velocity = p.deltaMovement.add(offset.normalize().scale(0.12 * kotlin.math.sqrt(1 + projectile.storedEnergy)))
            p.deltaMovement = if (velocity.length() > 4.0) velocity.normalize().scale(4.0) else velocity
            p.hurtMarked = true
        }
    }
    override fun onDie() {
        work.nekow.primalspells.magic.effect.visual.PixelVfx.retire(group); group = null
        level?.let { world -> work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(ParticleManager.of(world),
            Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()), Color.ofInt(245, 145, 80), 128, 0.32, 14) }
    }
}
