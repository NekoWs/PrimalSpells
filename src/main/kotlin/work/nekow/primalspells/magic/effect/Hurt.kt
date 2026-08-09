package work.nekow.primalspells.magic.effect

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.PrimalSpells.Companion.debug

class Hurt(private val amount: Double = 0.0) : BaseEffect() {
    override fun onHitEntity(target: Entity) {
        val dmg = if (amount > 0.0) amount else status.damage
        if (dmg <= 0.0) return
        val level = target.level() as? ServerLevel ?: return
        target.hurtServer(
            level,
            caster.damageSources().generic(),
            status.damage.toFloat()
        )
        caster.debug("Damaged ${target::class.simpleName}: $dmg")
    }
}
