package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore
import kotlin.math.sqrt

/**
 * 逐渐加速修正 —— 投射物初始速度降至 0.25 倍,
 * 在 40 tick 内线性加速至最高速度（初始速度的 5 倍 · √N）。
 */
class GradualAccelerate : Revise() {

    override val id = "gradual_accelerate"

    private val rampTicks = 40
    private val minMul = 0.25f
    private val maxMul = 5.0f

    init {
        mana = 4.0
        delay = 1

        effects += object : BaseEffect() {

            /** 施法瞬间的原始速度大小（仅首个实例捕获） */
            private var baseSpeed = 0f

            /**
             * 仅首个同类型实例降低初速度并记录原始速度,
             * 其余实例跳过以避免倍率叠加。
             */
            override fun onActive() {
                val cls = this::class
                val instances = projectile.effects.filter { it::class == cls }
                if (instances.firstOrNull() !== this) return

                baseSpeed = status.velocity.length()
                if (baseSpeed > 0.001f) {
                    status.velocity.normalize().mul(baseSpeed * minMul)
                }
            }

            /**
             * 每 tick 线性加速
             *
             * √N 叠加：仅首个同类型实例执行, 最高倍率 × √N
             */
            override fun onTick() {
                if (!projectile.alive) return
                if (baseSpeed <= 0.001f) return

                val cls = this::class
                val instances = projectile.effects.filter { it::class == cls }
                if (instances.firstOrNull() !== this) return

                val factor = sqrt(instances.size.toDouble()).toFloat()
                val adjustedMax = maxMul * factor

                val t = (status.age.toFloat() / rampTicks).coerceIn(0f, 1f)
                val currentMul = minMul + t * (adjustedMax - minMul)
                val desiredSpeed = baseSpeed * currentMul

                val currentLen = status.velocity.length()
                if (currentLen > 0.001f) {
                    status.velocity.normalize().mul(desiredSpeed)
                }
            }
        }

        lore(Lore.SPEED, "%.1f ~ %.0f".format(minMul, maxMul))
    }
}
