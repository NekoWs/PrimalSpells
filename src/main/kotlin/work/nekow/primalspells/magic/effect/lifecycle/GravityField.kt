package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 重力场 —— 在投射物所在位置维持一个持续 5 格半径的减速场。
 *
 * 每 tick 结算：范围内（含施法者）所有存活生物刷新迟缓 IV，并在 2.5 格球壳上撒末地烛粒子。
 * **离开范围立刻解除迟缓**（见 [AreaFieldEffect] 的「离开即解除」）。
 *
 * 粒子半径（2.5）小于生效半径（5.0）是刻意的：视觉收在中心附近，不代表实际作用范围。
 */
class GravityField : AreaFieldEffect(
    radius = 5.0,
    slownessAmplifier = 3,
    pullPerTick = 0.0,
    particle = ParticleTypes.END_ROD,
    particleCount = PARTICLE_COUNT,
    particleRadius = 2.5,
) {
    private companion object {
        const val PARTICLE_COUNT = 12
    }
}
