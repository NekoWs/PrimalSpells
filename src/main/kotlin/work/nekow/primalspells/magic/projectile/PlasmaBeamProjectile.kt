package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.PlasmaBeam

/** 短命等离子束源；基础四十tick，延寿按实际maxAge生效，伤害每 tick 结算。 */
class PlasmaBeamProjectile : Projectile() {
    override val id = "plasma_beam"
    override val bodyColor = Color.ofInt(76, 216, 255)
    init {
        selfDamage = true
        recoil = 0.15
        mass = 4.0
        damageType = work.nekow.primalspells.magic.core.SpellDamageType.ENERGY
        mana = 30.0; delay = 40; maxAge = 40; speed = 0f; dragCoeff = 0f
        status.damage = 1.5
        damageOncePerTick = true
        effects += PlasmaBeam()
    }
}
