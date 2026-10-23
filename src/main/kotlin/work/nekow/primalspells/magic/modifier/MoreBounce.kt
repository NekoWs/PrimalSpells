package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.geometry.Bounce

/** 增加八次方块反弹；实体穿刺不消耗次数，衰减由Projectile统一结算。 */
class MoreBounce : Modifier() {
    override val id = "bounce"
    init {
        mana = 2.0
        delay = 0
        effects += Bounce(8)
    }
}
