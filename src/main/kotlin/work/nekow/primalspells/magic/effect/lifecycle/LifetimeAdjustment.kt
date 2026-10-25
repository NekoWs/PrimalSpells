package work.nekow.primalspells.magic.effect.lifecycle

import work.nekow.primalspells.magic.effect.BaseEffect

/** 延寿与减寿先求和再统一钳制，在视觉激活前保证至少存在一刻。 */
class LifetimeAdjustment(val ticks: Int) : BaseEffect() {
    override fun onActive() {
        if (isStackPrimary) projectile.maxAge = (projectile.maxAge.toLong() +
            projectile.effects.filterIsInstance<LifetimeAdjustment>().sumOf { it.ticks.toLong() }).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }
}
