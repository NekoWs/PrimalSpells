package work.nekow.primalspells.magic.effect.lifecycle

import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime

/** 命中从已付费模板发射独立副本；一次性条件消费原荷载，均不重新抽牌/收费。 */
internal object PayloadRelease {
    fun emit(carrier: Projectile, payload: MutableList<Magic>, pos: Vector3d, direction: Vector3f,
             repeatable: Boolean = false) {
        if (payload.isEmpty()) return
        if (VoidStatus.isPhased(carrier.caster)) return
        val templates = payload.toList()
        if (!repeatable) payload.clear()
        val cards = if (repeatable) templates.map(SpellAssembly::copyCard) else templates
        val modifiers = cards.filterIsInstance<Modifier>()
        cards.filterIsInstance<work.nekow.primalspells.magic.core.OtherSpell>().forEach {
            if (!VoidStatus.isPhased(carrier.caster)) { it.caster = carrier.caster; it.wand = carrier.wand; it.spell() }
        }
        if (VoidStatus.isPhased(carrier.caster)) return
        cards.filterIsInstance<Projectile>().forEach { shot ->
            SpellAssembly.apply(shot, modifiers)
            shot.caster = carrier.caster; shot.wand = carrier.wand
            shot.position = Vector3d(pos); shot.velocity = Vector3f(direction)
            shot.spell()
            ProjectileRuntime.add(shot)
        }
    }
}
