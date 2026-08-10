package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 扩散修正 —— 增加爆炸范围，减少爆炸伤害。
 *
 * 效果：爆炸半径 ×2，爆炸伤害 ×0.5
 */
class Diffusion : Revise() {

    override val id = "diffusion"

    init {
        mana = 3.0
        delay = 1

        effects += object : BaseEffect() {

            /**
             * 激活时修改爆炸状态参数
             */
            override fun onActive() {
                status.explosionRadius *= 2.0f
                status.explosionDamage *= 0.5
            }
        }
    }
}
