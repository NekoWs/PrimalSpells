package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.PlasmaBeam

/** 短命等离子束源；延寿不会突破四十 tick 硬上限，伤害按五 tick 节拍结算。 */
class PlasmaBeamProjectile : Projectile() {
    override val id = "plasma_beam"
    override val bodyColor = Color.ofInt(76, 216, 255)
    init {
        mana = 30.0; delay = 40; maxAge = 40; speed = 0.12f; dragCoeff = 0f
        status.damage = 1.5
        effects += PlasmaBeam()
    }
}
