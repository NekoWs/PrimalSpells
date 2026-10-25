package work.nekow.primalspells.magic.effect.lifecycle

import work.nekow.primalspells.magic.effect.BaseEffect

/** 延寿与减寿先求和再统一钳制，在视觉激活前保证至少存在一刻。 */
class LifetimeAdjustment(val ticks: Int) : BaseEffect() {
    override fun onActive() {
        if (isStackPrimary) projectile.maxAge = resolve(projectile.maxAge,
            projectile.effects.filterIsInstance<LifetimeAdjustment>())
    }

    companion object {
        /** 主动状态与弹体共用寿命加值规则，先求和再钳制，避免牌序和整数溢出。 */
        fun resolve(base: Int, adjustments: List<LifetimeAdjustment>): Int =
            (base.toLong() + adjustments.sumOf { it.ticks.toLong() }).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }
}
