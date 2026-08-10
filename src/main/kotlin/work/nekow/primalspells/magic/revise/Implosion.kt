package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 聚爆修正 —— 减少爆炸范围，增加爆炸伤害。
 *
 * 效果：爆炸半径 ×0.5，爆炸伤害 ×2
 */
class Implosion : Revise() {

    override val id = "implosion"

    init {
        mana = 3.0
        delay = 1

        effects += object : BaseEffect() {

            /**
             * 激活时修改爆炸状态参数
             */
            override fun onActive() {
                status.explosionRadius *= 0.5f
                status.explosionDamage *= 2.0
            }
        }
    }
}
