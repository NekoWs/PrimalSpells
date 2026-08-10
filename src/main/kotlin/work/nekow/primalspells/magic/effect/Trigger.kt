package work.nekow.primalspells.magic.effect

import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.TriggerSpell
import work.nekow.primalspells.utils.HitResult

class Trigger : BaseEffect() {
    override fun onHit(result: HitResult) {
        val triggerSpell = projectile as? TriggerSpell ?: return
        val payload = triggerSpell.payload

        val revises = payload.filterIsInstance<Revise>().toList()
        revises.forEach { payload.remove(it) }

        payload.filterIsInstance<Projectile>().forEach { magic ->
            val effects = revises.flatMap { it.effects() }
            magic.effects += effects
            magic.caster = caster
            magic.wand = wand
            magic.position = result.pos
            magic.velocity = result.normal
            magic.spell()
            MagicManager.add(magic)
        }
    }
}
