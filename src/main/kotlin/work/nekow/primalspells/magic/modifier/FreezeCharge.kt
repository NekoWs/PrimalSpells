package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect

/** 冰冻附魔：牺牲四分之一直接伤害换取短暂控制，叠加不延长硬控制。 */
class FreezeCharge : Modifier() {
    override val id = "freeze_charge"
    init {
        mana = 8.0
        delay = 2
        effects += object : BaseEffect() {
            override fun onActive() {
                if (!isStackPrimary) return
                status.damage *= 0.75
                status.freezeTicks = 30
            }
        }
    }
}
