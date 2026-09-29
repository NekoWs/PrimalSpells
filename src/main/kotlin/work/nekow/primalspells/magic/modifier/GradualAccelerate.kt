package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore

/**
 * 逐渐加速修正 —— 施法瞬间把投射物速度乘到 0.25 倍，随后在 40 tick 内平滑加速到 5 倍（×√N）。
 *
 * **顺序无关**（见 [Modifier] 类注释的约定）：全程只做**乘法** ——
 * 施法时 `v *= minMul`，之后每 tick 乘「本 tick 倍率 ÷ 上一 tick 倍率」。
 * 于是与 `speed_boost`（×2）、`gradual_decelerate` 等叠加时不会互相覆盖，
 * 也不依赖谁先装配（旧实现用 `normalize().mul(绝对目标速度)`，会把其它修饰器的速度改动抹掉）。
 *
 * 多个实例叠加：仅首个实例生效，最高倍率 × √N。
 */
class GradualAccelerate : Modifier() {

    override val id = "gradual_accelerate"

    private val rampTicks = 40
    private val minMul = 0.25f
    private val maxMul = 5.0f

    init {
        mana = 4.0
        delay = 1

        effects += object : BaseEffect() {

            /** 仅首个同类型实例把初速乘到下限倍率（乘法，可交换） */
            override fun onActive() {
                if (!isStackPrimary) return
                status.velocity.mul(minMul)
            }

            /** 每 tick 乘「本 tick 倍率 ÷ 上一 tick 倍率」，把速度平滑推到最高倍率 */
            override fun onTick() {
                if (!projectile.alive) return
                if (!isStackPrimary) return

                val prev = mulAt(status.age - 1)
                val cur = mulAt(status.age)
                if (prev <= 1e-4f) return
                status.velocity.mul(cur / prev)
            }

            /** 第 [tick] tick 时的目标倍率（相对施法瞬间的速度） */
            private fun mulAt(tick: Int): Float {
                val t = (tick.toFloat() / rampTicks).coerceIn(0f, 1f)
                return minMul + t * (maxMul * stackScale - minMul)
            }
        }

        lore(Lore.SPEED, "%.1f ~ %.0f".format(minMul, maxMul))
    }
}
