package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore

class SpeedUp: Revise() {
    override val id = "speed_up"

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