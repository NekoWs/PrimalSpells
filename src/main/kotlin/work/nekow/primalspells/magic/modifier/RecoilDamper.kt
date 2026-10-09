package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect

class RecoilDamper : Modifier() {
    override val id = "recoil_damper"
    init {
        mana = 8.0
        effects += object : BaseEffect() {
            override fun onActive() { projectile.recoilMultiplier *= 0.25 }
        }
    }
}
