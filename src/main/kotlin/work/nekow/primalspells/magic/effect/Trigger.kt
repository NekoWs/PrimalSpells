package work.nekow.primalspells.magic.effect

import work.nekow.primalspells.PrimalSpells.Companion.debug
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.TriggerSpell
import work.nekow.primalspells.utils.HitResult

class Trigger : BaseEffect() {
    override fun onHit(result: HitResult) {
        val triggerSpell = projectile as? TriggerSpell ?: return
        val payload = triggerSpell.payload
        val effects = payload.filterIsInstance<Revise>()
            .flatMap {
                payload.remove(it)
                it.effects()
            }
        payload.filterIsInstance<Projectile>().forEach { magic ->
            val p = magic.clone()
            p.effects += effects
            p.caster = caster
            p.wand = wand
            p.position = result.pos
            p.velocity = result.normal
            p.spell()
            caster.debug("Triggered: ${wand.renderTriggers(p)}")
            MagicManager.add(p)
        }
    }
}
