package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect

/** 无限实体穿透、允许自伤并绕过护盾；Move按接触时间结算重复命中，增加10t施放延迟。 */
class Piercing : Modifier() {
    override val id = "piercing"

    init {
        mana = 200.0
        delay = 10
        recharge = 0
        effects += object : BaseEffect() {
            override fun onActive() {
                projectile.maxHitTargets = -1
                projectile.selfDamage = true
            }
        }
    }
}
