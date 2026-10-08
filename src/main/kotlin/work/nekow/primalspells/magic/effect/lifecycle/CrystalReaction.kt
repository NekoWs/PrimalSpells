package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.util.HitType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 休眠水晶生命周期：粘附、预警、排队爆炸；爆炸通知只改状态，避免递归爆炸与修改遍历列表。 */
class CrystalReaction : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var level: ServerLevel? = null
    private var group: ParticleGroup? = null
    private var lastCenter: Vec3? = null
    private var countdown = -1
    private var detonated = false
    private var stuck = false

    /** 预警状态供场景验收及将来的 HUD 读取，不开放状态改写。 */
    val isArmed: Boolean get() = countdown >= 0

    override fun onActive() { level = caster.level() as? ServerLevel }

    override fun onHit(result: HitResult) {
        if (result.type != HitType.BLOCK || stuck) return
        stuck = true
        projectile.alive = true
        projectile.motionEnabled = false
        status.pos.set(result.pos)
        status.velocity.zero()
    }

    /** 受爆炸影响或由同一施法者引爆；多次通知不会重复排队。 */
    fun arm() {
        if (countdown >= 0 || detonated || !projectile.alive) return
        countdown = 3
        group?.recolor(Color.ofInt(255, 185, 82, 255), 2)?.pulse(1.25f, 2, 1)
    }

    override fun onTick() {
        val world = level ?: return
        if (!projectile.alive) return
        // 跨维度施法者不把旧坐标带到新维度；水晶直接清理。
        if (caster.level() !== world) { projectile.alive = false; return }
        val owned = MagicManager.projectiles.filter { it.id == "dormant_crystal" && it.alive && it.caster === caster }
        if (owned.size > 24 && owned.indexOf(projectile) < owned.size - 24) { projectile.alive = false; return }
        val center = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
            .add(0.0, if (stuck) 0.45 else 0.0, 0.0)
        if (group == null) {
            val manager = ParticleManager.of(world)
            val crystal = manager.createGroup(center)
            repeat(4) { side ->
                val angle = side * PI / 2
                val rim = center.add(cos(angle) * 0.3, 0.0, sin(angle) * 0.3)
                val top = center.add(0.0, 0.65, 0.0)
                val bottom = center.add(0.0, -0.45, 0.0)
                Draw.curve(manager, { t -> if (t < 0.5) top.lerp(rim, t * 2) else rim.lerp(bottom, (t - 0.5) * 2) },
                    48, ColorSource.of(Color.ofInt(178, 118, 255, 255)), 0.75f, group = crystal,
                    visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false))
            }
            crystal.spin(Vec3(0.0, 1.0, 0.0), 0.025).pulse(1.07f, 12)
            group = crystal
            if (countdown >= 0) crystal.recolor(Color.ofInt(255, 185, 82, 255), 2)
        }
        if (lastCenter != center) group?.setPivot(center)
        lastCenter = center
        if (countdown >= 0 && countdown-- == 0) {
            detonated = true
            projectile.alive = false
            Explosion.customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage, world)
        }
    }

    override fun onDie() {
        group?.stopContinuous()?.scale(0.05f, 10, EasingType.LINEAR)?.destroyAfter(10)
        group = null
    }

    companion object {
        /** 在实际爆炸范围内激活水晶；无递归、不会激活别的维度。 */
        fun notifyExplosion(level: ServerLevel, center: Vec3, radius: Double) {
            MagicManager.projectiles.filter { it.alive && it.caster.level() === level &&
                Vec3(it.status.pos.x.toDouble(), it.status.pos.y.toDouble(), it.status.pos.z.toDouble()).distanceToSqr(center) <= radius * radius
            }.forEach { p -> p.effects.filterIsInstance<CrystalReaction>().forEach { it.arm() } }
        }
        /** 专用引爆术只激活该施法者在十六格内布置的水晶。 */
        fun detonateOwned(source: net.minecraft.world.entity.Entity, center: Vec3) {
            MagicManager.projectiles.filter { it.alive && it.caster === source &&
                Vec3(it.status.pos.x.toDouble(), it.status.pos.y.toDouble(), it.status.pos.z.toDouble()).distanceToSqr(center) <= 256.0
            }.forEach { p -> p.effects.filterIsInstance<CrystalReaction>().forEach { it.arm() } }
        }
    }
}
