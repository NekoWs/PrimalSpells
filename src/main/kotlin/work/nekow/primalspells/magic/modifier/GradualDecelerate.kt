package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore

/**
 * 逐渐减速修正 —— 施法瞬间把投射物速度乘到 2 倍，随后在 40 tick 内平滑降到 0.22 倍（÷√N）。
 *
 * **顺序无关**（见 [Modifier] 类注释的约定）：全程只做**乘法** ——
 * 施法时 `v *= maxMul`，之后每 tick 乘「本 tick 倍率 ÷ 上一 tick 倍率」。
 * 与 `gradual_accelerate`、`speed_boost` 等叠加时不会互相覆盖。
 *
 * 多个实例叠加：仅首个实例生效，最低倍率 ÷ √N。
 */
class GradualDecelerate : Modifier() {

    override val id = "gradual_decelerate"

    private val rampTicks = 40
    private val maxMul = 2.0f
    private val minMul = 0.3f

    init {
        mana = 4.0
        delay = 0

        effects += object : BaseEffect() {

            /** 仅首个同类型实例把初速乘到上限倍率（乘法，可交换） */
            override fun onActive() {
                if (!isStackPrimary) return
                status.velocity.mul(maxMul)
            }

            /** 每 tick 乘「本 tick 倍率 ÷ 上一 tick 倍率」，把速度平滑压到最低倍率 */
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
                val floorMul = minMul / stackScale
                return (maxMul - t * (maxMul - floorMul)).coerceAtLeast(0.01f)
            }
        }

        lore(Lore.SPEED, "%.0f ~ %.1f".format(maxMul, minMul))
    }
}
