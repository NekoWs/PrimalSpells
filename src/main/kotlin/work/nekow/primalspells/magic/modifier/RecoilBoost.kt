package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect

class RecoilBoost : Modifier() {
    override val id = "recoil_boost"
    init {
        mana = 5.0
        effects += object : BaseEffect() {
            override fun onActive() { projectile.recoilBonus += 0.35 }
        }
    }
}
