package work.nekow.primalspells.magic

import work.nekow.primalspells.magic.effect.HitEntity
import work.nekow.primalspells.magic.effect.Trigger

abstract class TriggerMagic : Projectile(), TriggerSpell {
    override var payload = arrayListOf<Magic>()
    override var triggerCast = 1

    init {
        effects += HitEntity(1.0)
        effects += Trigger()
    }
}
