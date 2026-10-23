package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.geometry.Bounce
import work.nekow.primalspells.magic.effect.lifecycle.Explosion

/** 每次成功弹跳时触发爆炸，使用所属投射物的爆炸参数。 */
class BounceExplosion : Modifier() {
    override val id = "bounce_explosion"

    init {
        mana = 5.0
        delay = 0
        effects += Bounce()
        effects += Explosion(onHit = true, onDeath = false, bounceOnly = true)
    }
}
