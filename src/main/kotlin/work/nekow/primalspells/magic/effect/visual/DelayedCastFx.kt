package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.toVec3

/** 随弹体收拢的六枚锁印；只有真实到期才在最终位置打开，绝不提前播放爆开。 */
class DelayedCastFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var manager: ParticleManager? = null
    private var batch: ParticleBatch? = null
    private var finished = false
    var releasedAtAge: Int = -1
        private set

    private fun center() = status.pos.toVec3()
    private fun points(): List<Vec3> {
        val radius = (0.1 + 0.3 * (1.0 - status.age.toDouble() / projectile.maxAge.coerceAtLeast(1))) * projectile.areaScale
        val center = center()
        return listOf(center, center.add(radius, 0.0, 0.0), center.add(-radius, 0.0, 0.0),
            center.add(0.0, radius, 0.0), center.add(0.0, -radius, 0.0),
            center.add(0.0, 0.0, radius), center.add(0.0, 0.0, -radius))
    }

    override fun onActive() {
        val world = caster.level() as? ServerLevel ?: return
        manager = ParticleManager.of(world)
        val specs = points().mapIndexed { index, point ->
            ParticleSpawnSpec().position(point).color(if (index == 0) Color.ofInt(218, 255, 187) else Color.GREEN)
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(
                    if (index == 0) 0.1875f else 0.125f, if (index == 0) 0.1875f else 0.125f).glowing(true))
                .lifetime(-1).sizeCurve(CurveKey.at(0, 0), CurveKey.at(2, 1, EasingType.EASE_OUT))
        }
        batch = ParticleBatch(manager!!).also { it.addAll(manager!!.spawnAll(specs)) }
    }

    override fun onTick() { if (!finished) batch?.trackAll(points()) }

    override fun onExpire() {
        if (finished) return
        releasedAtAge = status.age
        val pm = manager ?: return
        batch?.trackAll(points())
        finish()
        PixelVfx.burst(pm, center(), Color.GREEN, 40, 0.18, 8, 0.006)
        PixelVfx.burst(pm, center(), Color.ofInt(218, 255, 187), 8, 0.1, 4, 0.0)
    }

    private fun finish() {
        if (finished) return
        finished = true
        manager?.let { PixelVfx.retireBatch(it, batch, center(), 2) }
        batch = null
    }

    override fun onDie() { finish() }
}
