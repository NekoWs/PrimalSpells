package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.util.HitType

/** 休眠水晶生命周期：粘附、预警、排队爆炸；爆炸通知只改状态，避免递归爆炸与修改遍历列表。 */
class CrystalReaction : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var level: ServerLevel? = null
    private var group: ParticleGroup? = null
    private var lastCenter: Vec3? = null
    private var countdown = -1
    private var detonated = false
    private var stuck = false
    private var visualScale = 1.0
    private val charged = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<work.nekow.primalspells.magic.projectile.Projectile, Boolean>())

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
        val center = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
            .add(0.0, if (stuck) 0.45 else 0.0, 0.0)
        if (group == null) {
            group = work.nekow.primalspells.magic.effect.visual.PixelVfx.crystal(ParticleManager.of(world), center, status.freezeTicks > 0)
            if (countdown >= 0) group?.recolor(Color.ofInt(255, 185, 82), 1)
        }
        if (visualScale != projectile.areaScale) {
            group?.scale((projectile.areaScale / visualScale).toFloat(), 1)
            visualScale = projectile.areaScale
        }
        if (status.age % 12 == 0) work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(
            ParticleManager.of(world), center.add(0.0, 0.85, 0.0), Color.ofInt(210, 245, 210), 8, 0.02, 5, -0.002)
        if (lastCenter != center) group?.setPivot(center)
        lastCenter = center
        if (countdown >= 0 && countdown-- == 0) {
            detonated = true
            projectile.alive = false
            Explosion.customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage, world, projectile)
        }
    }

    /** 普通弹/电弧/光束打入晶簇会储能并引爆；同一弹体只充能一次。 */
    fun charge(incoming: work.nekow.primalspells.magic.projectile.Projectile) {
        if (incoming.id == "dormant_crystal" || !charged.add(incoming)) return
        projectile.storedEnergy += incoming.status.damage.coerceAtLeast(1.0)
        status.explosionDamage += incoming.status.damage.coerceAtLeast(1.0) * 0.5
        if (incoming.status.freezeTicks > 0) status.freezeTicks = incoming.status.freezeTicks
        arm()
        level?.let { world -> work.nekow.primalspells.magic.effect.visual.PixelVfx.arc(ParticleManager.of(world),
            Vec3(incoming.status.pos.x.toDouble(), incoming.status.pos.y.toDouble(), incoming.status.pos.z.toDouble()),
            Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()), Color.ofInt(224, 174, 65)) }
    }

    override fun onDie() {
        group?.remove()
        level?.let { world -> work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(ParticleManager.of(world),
            Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()),
            if (status.freezeTicks > 0) Color.ofInt(119, 213, 240) else Color.ofInt(114, 216, 185), 144, 0.25, 16) }
        charged.clear()
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
        fun detonateOwned(source: net.minecraft.world.entity.Entity, center: Vec3, detonator: work.nekow.primalspells.magic.projectile.Projectile? = null) {
            MagicManager.projectiles.filter { it.alive && it.caster === source &&
                Vec3(it.status.pos.x.toDouble(), it.status.pos.y.toDouble(), it.status.pos.z.toDouble()).distanceToSqr(center) <= 256.0 * (detonator?.areaScale ?: 1.0) * (detonator?.areaScale ?: 1.0)
            }.forEach { p ->
                if (detonator != null) work.nekow.primalspells.magic.core.SpellAssembly.reinforce(detonator, p)
                p.effects.filterIsInstance<CrystalReaction>().forEach { it.arm() }
            }
        }
    }
}
