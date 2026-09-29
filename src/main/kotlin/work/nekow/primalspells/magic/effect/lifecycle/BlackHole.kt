package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 黑洞 —— 在投射物所在位置维持一个持续 5 格半径的引力场。
 *
 * 每 tick 结算：范围内所有存活生物（含施法者）刷新迟缓 I，并被拉向中心
 * （每 tick `0.12 / (距离 + 0.5)`，等价于旧实现「每 5 tick 施加 0.6」的力度）；
 * 同时在 5 格球壳上撒 256 个传送门粒子（静止粒子，当前仅用于观察黑洞范围，后续会优化）。
 * **离开范围立刻解除迟缓**（见 [AreaFieldEffect] 的「离开即解除」）。
 */
class BlackHole : AreaFieldEffect(
    radius = 5.0,
    slownessAmplifier = 0,
    pullPerTick = 0.12,
    particle = ParticleTypes.PORTAL,
    particleCount = PARTICLE_COUNT,
    particleRadius = 5.0,
) {
    private companion object {
        const val PARTICLE_COUNT = 256
    }
}
