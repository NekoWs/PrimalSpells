package work.nekow.primalspells.magic.projectile.immobile

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

class FeatherWard : UtilityPulse() {
    override val id = "feather_ward"
    init { mana = 15.0 }
    override fun onSpell() {
        val target = caster as? LivingEntity ?: return
        if (!allowed(target)) return
        target.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, true))
        target.fallDistance = 0.0
    }
}
