package work.nekow.primalspells.magic.projectile.s_projectile

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.GravityField
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.utils.Lore

/**
 * 重力场 —— 静态投射物，存在 200 tick（10 秒），
 * 对直径 5 格球体范围内实体施加减速药水效果。
 */
class GravityFieldProjectile : Projectile() {

    override val id = "gravity_field"

    init {
        mana = 10.0
        delay = 5
        recharge = 10
        maxAge = 200 // 存活 10 秒
        speed = 0f // 静态
        hitRadius = 0.0 // 无碰撞
        effects += Trajectory(ParticleTypes.PORTAL) // 粒子效果
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
