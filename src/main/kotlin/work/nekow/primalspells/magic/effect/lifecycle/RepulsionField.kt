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
                    val a = t * PI * 2; val u = 4 * cos(a); val v = 4 * sin(a)
                    center.add(if (plane == 2) 0.0 else u, if (plane == 0) 0.0 else v,
                        if (plane == 0) v else if (plane == 2) u else 0.0)
                }, 96, ColorSource.of(Color.ofInt(245, 145, 80, 120)), 0.6f, group = shell,
                    visual = ParticleVisual().style(ParticleStyle.SOFT_DOT).glowing(true).additive(true))
            }
            shell.fadeIn(8).spin(Vec3(0.2, 1.0, 0.1).normalize(), 0.025).pulse(1.04f, 10)
            group = shell
        }
        if (lastCenter != center) group?.setPivot(center)
        lastCenter = center
        for (p in MagicManager.projectiles) {
            if (p === projectile || !p.alive || !p.motionEnabled || p.caster.level() !== world || p.speed == 0f) continue
            val offset = Vec3(p.status.pos.x.toDouble(), p.status.pos.y.toDouble(), p.status.pos.z.toDouble()).subtract(center)
            if (offset.lengthSqr() !in 1e-6..16.0) continue
            val push = offset.normalize().scale(0.12)
            p.status.velocity.add(push.x.toFloat(), push.y.toFloat(), push.z.toFloat())
            if (p.status.velocity.length() > 4f) p.status.velocity.normalize().mul(4f)
        }
        for (p in world.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile::class.java,
            AABB.ofSize(center, 8.0, 8.0, 8.0)) { it.isAlive }) {
            val offset = p.position().subtract(center)
            if (offset.lengthSqr() !in 1e-6..16.0) continue
            val velocity = p.deltaMovement.add(offset.normalize().scale(0.12))
            p.deltaMovement = if (velocity.length() > 4.0) velocity.normalize().scale(4.0) else velocity
            p.hurtMarked = true
        }
    }
    override fun onDie() { group?.fadeOut(8, removeAfter = true); group = null }
}
