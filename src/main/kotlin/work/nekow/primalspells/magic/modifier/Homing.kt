package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.geometry.Tracking

/** 一格短距强追踪；锁定生物中心，多卡按√N增加范围，不增加弹速。 */
class Homing : Modifier() {
    override val id = "homing"
    init {
        mana = 4.0
        delay = 0
        effects += Tracking(range = 1.0f, strength = 1.0f)
    }
}
