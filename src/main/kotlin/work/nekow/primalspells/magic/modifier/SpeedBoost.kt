package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore

class SpeedBoost: Modifier() {
    override val id = "speed_boost"

    init {
        mana = 3.0
        effects += object: BaseEffect() {
            override fun onActive() {
                status.velocity.mul(2f)
            }
        }
        lore(Lore.SPEED, 2)
    }
}