package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore

/**
 * 往复运动修正 —— 增加投射物 40 tick 存在时间,
 * 每隔 30 tick 使投射物运动方向反转。
 *
 * 多个修正叠加：存在时间线性叠加（+40t/个），仅首个实例执行反转。
 */
class ReciprocateRevise : Revise() {

    override val id = "reciprocate"

    /** 每个实例增加的额外存在时间（tick） */
    private val extraAgePerInstance = 40

    /** 方向反转周期（tick） */
    private val periodTicks = 30

    init {
        mana = 3.0
        delay = 1

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
             * 仅首个同类型实例执行反转逻辑，避免重复反转
             */
            override fun onTick() {
                if (!projectile.alive) return

                // 仅首个实例处理方向反转
                val cls = this::class
                val instances = projectile.effects.filter { it::class == cls }
                if (instances.firstOrNull() !== this) return

                if (status.age > 0 && status.age % periodTicks == 0) {
                    status.velocity.mul(-1f)
                }
            }
        }

        lore(Lore.MAX_AGE, "+$extraAgePerInstance")
    }
}
