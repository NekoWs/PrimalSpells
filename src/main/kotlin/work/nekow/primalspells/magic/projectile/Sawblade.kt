package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.Hurt

class Sawblade : Projectile() {
    override val id = "sawblade"
    override val bodyColor = Color.WHITE
    init {
        damageType = work.nekow.primalspells.magic.core.SpellDamageType.SLICE
        mana = 25.0; delay = 8; recharge = 5; maxAge = 100
        damageOncePerTick = true; status.damage = 12.0; selfDamage = true; maxBounces = 8; maxHitTargets = 0
        bounceScaler = org.joml.Vector3f(1f); speed = 0.65f; dragCoeff = 0f; hitRadius = 0.42
        effects += work.nekow.primalspells.magic.effect.visual.SawbladeFx()
        effects += Hurt()
    }
}
