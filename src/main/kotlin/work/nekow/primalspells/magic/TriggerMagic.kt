package work.nekow.primalspells.magic

import work.nekow.primalspells.magic.effect.HitEntity
import work.nekow.primalspells.magic.effect.Trigger
import work.nekow.primalspells.wand.SpellChain

abstract class TriggerMagic : Projectile(), TriggerSpell {
    override var payload: SpellChain? = null
    override var cast = 0

    init {
        effects += HitEntity(1.0)
        effects += Trigger()
    }
}
