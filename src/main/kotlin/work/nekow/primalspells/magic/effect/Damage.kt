package work.nekow.primalspells.magic.effect

import net.minecraft.world.entity.Entity

class Damage(private val amount: Double = 0.0) : BaseEffect() {
    override fun onHitEntity(target: Entity) {
        val dmg = if (amount > 0.0) amount else status.damage
        if (dmg <= 0.0) return
        target.hurt(caster.damageSources().generic(), dmg.toFloat())
    }
}
