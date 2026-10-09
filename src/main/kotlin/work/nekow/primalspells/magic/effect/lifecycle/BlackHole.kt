package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.effect.visual.BlackHoleFx
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ScreenShake
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity

/**
 * 半径五格的黑洞：生物迟缓 I，按0.6/(距离+0.5)强拉向中心；暗核每刻撕裂一次。
 * 服务端结算由 AreaFieldEffect 驱动，暗核与星尘表现委托 BlackHoleFx。
 * 仅形成时近场轻触；视觉退场由 PD 组自行播放，不再重建三拍球壳。
 */
class BlackHole : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 0,
    pullPerTick = PULL_PER_TICK,
), DirectDamage {
    override val damageStrength: Double get() = status.damage
    private val visuals = BlackHoleFx()
    private var opened = false

    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        visuals.spawn(manager, center, projectile.areaScale)
        if (!opened) {
            opened = true
            ScreenShake.shake(manager.level, center, radius = 6.0, amplitude = 0.16f, ticks = 8)
        }
    }

    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        visuals.update(center)
    }

    override fun clearVisuals(manager: ParticleManager, center: Vec3) {
        visuals.clear(manager)
        if (projectile.storedEnergy > 0) Explosion.customExplode(caster, org.joml.Vector3f(status.pos),
            (3.0 * projectile.areaScale).toFloat(), projectile.storedEnergy, manager.level, projectile)
    }

    /** 每 tick 对范围内投射物施加原有引力；与视觉盘的半径、转速无关。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        work.nekow.primalspells.magic.core.MagicMass.attract(projectile, dirToCenter, PULL_PER_TICK / (dist + 0.5))
    }

    override fun affectsEntity(entity: LivingEntity): Boolean =
        entity !is net.minecraft.world.entity.player.Player || !(entity.isCreative && entity.abilities.flying)

    override fun affectEntity(level: ServerLevel, entity: LivingEntity, center: Vec3, dist: Double) {
        if (dist <= 1.15 * projectile.areaScale)
            SpellDamage.hit(level, entity, projectile, status.damage, knockback = false)
    }

    private companion object {
        const val EFFECT_RADIUS = 5.0
        const val PULL_PER_TICK = 0.6
    }
}
