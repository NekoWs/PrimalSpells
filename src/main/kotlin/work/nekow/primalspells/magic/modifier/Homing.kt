package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.geometry.Tracking

/** 六格范围锁定生物中心，沿真实飞行子步平滑转向；多卡按√N扩展，不增加弹速。 */
class Homing : Modifier() {
    override val id = "homing"
    init {
        mana = 4.0
        delay = 0
        effects += Tracking(range = 6.0f, strength = 1.0f)
    }
}
