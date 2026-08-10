package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore
import kotlin.math.sqrt

/**
 * 逐渐减速修正 —— 投射物初始速度提升至 5 倍,
 * 在 40 tick 内线性减速至最低速度（初始速度的 0.25 倍 ÷ √N）。
 */
class GradualDecelerate : Revise() {

    override val id = "gradual_decelerate"

    private val rampTicks = 40
    private val maxMul = 5.0f
    private val minMul = 0.25f

    init {
        mana = 4.0
        delay = 1

        effects += object : BaseEffect() {

            /** 施法瞬间的原始速度大小 */
            private var baseSpeed = 0f

            /**
             * 记录原始速度并提升至最高倍率
             */
            override fun onActive() {
                baseSpeed = status.velocity.length()
                if (baseSpeed > 0.001f) {
                    status.velocity.normalize().mul(baseSpeed * maxMul)
                }
            }

            /**
             * 每 tick 线性减速
             *
             * √N 叠加：仅首个同类型实例执行，最低倍率 ÷ √N
             */
            override fun onTick() {
                if (!projectile.alive) return
                if (baseSpeed <= 0.001f) return

                // 统计同类型效果实例数，仅首个执行
                val cls = this::class
                val instances = projectile.effects.filter { it::class == cls }
                if (instances.firstOrNull() !== this) return

                val factor = sqrt(instances.size.toDouble()).toFloat()
                val adjustedMin = minMul / factor

                // 线性插值 t ∈ [0, 1]
                val t = (status.age.toFloat() / rampTicks).coerceIn(0f, 1f)
                val currentMul = (maxMul - t * (maxMul - adjustedMin)).coerceAtLeast(0.01f)
                val desiredSpeed = baseSpeed * currentMul

                // 保持方向，修改速度大小
                val currentLen = status.velocity.length()
                if (currentLen > 0.001f) {
                    status.velocity.normalize().mul(desiredSpeed)
                }
            }
        }

        lore(Lore.SPEED, "%.0f ~ %.1f".format(maxMul, minMul))
    }
}
