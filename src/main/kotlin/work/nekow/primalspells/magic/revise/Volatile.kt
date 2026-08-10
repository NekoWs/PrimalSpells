package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 易爆修正 —— 增加爆炸伤害。
 *
 * 效果：爆炸伤害 ×2
 */
class Volatile : Revise() {

    override val id = "volatile"

    init {
        mana = 2.0
        delay = 1

        effects += object : BaseEffect() {

            /**
             * 激活时修改爆炸伤害
             */
            override fun onActive() {
                status.explosionDamage *= 2.0
            }
        }
    }
}
