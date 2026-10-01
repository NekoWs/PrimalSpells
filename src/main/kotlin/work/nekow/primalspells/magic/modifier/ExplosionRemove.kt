package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 移除爆炸修正 —— 把爆炸强度乘到 0（伤害与半径都归零）。
 *
 * 用**乘法**而不是覆写赋值：所有修饰器对投射物状态的作用必须是**可交换的**
 * （见 `Modifier` 的 KDoc），这样「伤害强化 + 移除爆炸 + 火球」不论按什么顺序装配，
 * 结果都一样。
 */
class ExplosionRemove : Modifier() {

    override val id = "explosion_remove"

    init {
        mana = 1.0
        delay = 0

        effects += object : BaseEffect() {

            /** 激活时把爆炸强度乘到 0（乘法吸收，与其它修饰器顺序无关） */
            override fun onActive() {
                status.explosionDamage *= 0.0
                status.explosionRadius *= 0f
            }
        }
    }
}
