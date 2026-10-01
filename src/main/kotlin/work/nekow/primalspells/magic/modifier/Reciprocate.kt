package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore

/**
 * 往复运动修正 —— 增加投射物 40 tick 存在时间,
 * 每隔 30 tick 使投射物运动方向反转。
 *
 * 多个修正叠加：存在时间线性叠加（+40t/个），仅首个实例执行反转。
 */
class Reciprocate : Modifier() {

    override val id = "reciprocate"

    /** 每个实例增加的额外存在时间（tick） */
    private val extraAgePerInstance = 40

    /** 方向反转周期（tick） */
    private val periodTicks = 30

    init {
        mana = 3.0
        delay = 0

        effects += object : BaseEffect() {

            /**
             * 激活时增加投射物最大存活时间
             */
            override fun onActive() {
                projectile.maxAge += extraAgePerInstance
            }

            /**
             * 每 tick 检测是否到达反转周期
             *
             * 仅首个同类型实例执行反转逻辑，避免 N 个实例把方向反复翻回来（见 [BaseEffect.isStackPrimary]）
             */
            override fun onTick() {
                if (!projectile.alive) return
                if (!isStackPrimary) return

                if (status.age > 0 && status.age % periodTicks == 0) {
                    status.velocity.mul(-1f)
                }
            }
        }

        lore(Lore.MAX_AGE, "+$extraAgePerInstance")
    }
}
