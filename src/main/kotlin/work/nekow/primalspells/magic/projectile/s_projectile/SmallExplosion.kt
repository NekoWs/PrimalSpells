package work.nekow.primalspells.magic.projectile.s_projectile

import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.Explosion

/**
 * 小型爆炸 —— 静态投射物，初速度为 0，
 * 存活 1 tick 后死亡并引发爆炸。
 */
class SmallExplosion : Projectile() {

    override val id = "small_explosion"

    init {
        mana = 2.0
        delay = 1
        recharge = 2

        /** 存活 1 tick 后死亡 */
        maxAge = 1

        /** 初速度 = 0（静态） */
        speed = 0f

        /** 无需碰撞检测 */
        hitRadius = 0.0

        // 死亡时触发爆炸
        effects += Explosion(onDeath = true, onHit = false)
    }

    /**
     * 施放后设置爆炸参数
     */
    override fun onSpell() {
        status.explosionRadius = 3.0f
        status.explosionDamage = 4.0
        status.explosionLevel = 0
    }
}
