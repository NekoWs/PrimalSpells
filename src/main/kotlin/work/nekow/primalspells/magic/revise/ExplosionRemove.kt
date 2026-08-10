package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 移除爆炸修正 —— 将所有爆炸参数置为 0。
 *
 * 效果：爆炸伤害 = 0，爆炸半径 = 0，爆炸等级 = 0
 */
class ExplosionRemove : Revise() {

    override val id = "explosion_remove"

    init {
        mana = 1.0
        delay = 1

        effects += object : BaseEffect() {

            /**
             * 激活时清零所有爆炸参数
             */
            override fun onActive() {
                status.explosionDamage = 0.0
                status.explosionRadius = 0.0f
                status.explosionLevel = 0
            }
        }
    }
}
