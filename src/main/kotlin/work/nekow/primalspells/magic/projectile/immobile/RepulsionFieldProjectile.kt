package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.RepulsionField

/** 定点斥力场；存活六秒，作用半径四格，轨道修饰器可使场心移动。 */
class RepulsionFieldProjectile : Projectile() {
    override val id = "repulsion_field"
    init { mana = 20.0; delay = 30; maxAge = 120; speed = 0f; collisionEnabled = false; effects += RepulsionField() }
}
