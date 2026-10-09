package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.Hurt

class HealingBolt : Projectile() {
    override val id = "healing_bolt"
    override val bodyColor = Color.GREEN
    init {
        damageType = work.nekow.primalspells.magic.core.SpellDamageType.HEALING
        mana = 20.0; delay = 8; recharge = 3; maxAge = 120
        status.damage = 5.0; selfDamage = true; speed = 0.65f; hitRadius = 0.2
        effects += work.nekow.primalspells.magic.effect.visual.Trajectory(bodyColor)
        effects += Hurt()
    }
}
