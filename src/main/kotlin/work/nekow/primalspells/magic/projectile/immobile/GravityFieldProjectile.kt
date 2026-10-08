package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.GravityField
import work.nekow.primalspells.magic.effect.visual.Trajectory
import work.nekow.primalspells.util.Lore

/**
 * 重力场，对直径 5 格球体范围内实体施加减速药水效果。
 */
class GravityFieldProjectile : Projectile() {

    override val id = "gravity_field"
    override val bodyColor = Color.of(0.5f, 0.4f, 1f)

    init {
        mana = 10.0
        delay = 0
        recharge = 0
        maxAge = 200 // 存活 10 秒
        speed = 0f // 静态
        collisionEnabled = false
        hitRadius = 0.0 // 无碰撞
        effects += Trajectory(Color.of(0.5f, 0.4f, 1f)) // 粒子效果
        effects += GravityField() // 减速效果
    }

    override fun onSpell() {
        // 无额外初始化
    }

    override fun initLore() {
        super.initLore()
        lore(Lore.DESCRIPTION)
    }
}
