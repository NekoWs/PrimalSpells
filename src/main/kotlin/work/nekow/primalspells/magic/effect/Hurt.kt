package work.nekow.primalspells.magic.effect

import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.ModDamageTypes
import work.nekow.primalspells.PrimalSpells.Companion.debug

class Hurt(private val amount: Double = 0.0) : BaseEffect() {
    override fun onHitEntity(target: Entity) {
        val dmg = if (amount > 0.0) amount else status.damage
        if (dmg <= 0.0) return
        val level = target.level() as? ServerLevel ?: return
        val damageType = level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE)
            .getOrThrow(ModDamageTypes.PROJECTILE_KEY)
        val source = DamageSource(damageType, caster)
        val invulnerableTime = target.invulnerableTime
        target.invulnerableTime = 0
        target.hurtServer(level, source, dmg.toFloat())
        target.invulnerableTime = invulnerableTime
        caster.debug("Damaged ${target::class.simpleName} $dmg")
    }
}
