package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore

/** 无限实体穿透、自伤和绕盾；只扣投射物伤害，不改变冷却或其它伤害渠道。 */
class Piercing : Modifier() {
    override val id = "piercing"

    init {
        mana = 300.0
        delay = 0
        recharge = 0
        effects += object : BaseEffect() {
            override fun onActive() {
                projectile.maxHitTargets = -1
                projectile.selfDamage = true
                if (projectile.damageType == SpellDamageType.PROJECTILE) status.damage -= 15.0
            }
        }
        lore(Lore.TYPED_DAMAGE, SpellDamageType.PROJECTILE.text, "-15")
    }
}
