package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 命中实体造成伤害。
 *
 * @param amount 固定伤害；`<= 0` 时改用投射物当前的 `status.damage`（可被伤害类修饰器改写）
 *
 * 两个刻意的实现细节：
 * 1. 结算前把目标的 `invulnerableTime` 临时清零再恢复 —— 这样**同一 tick 的多次命中**与
 *    **跨 tick 的持续命中**都能真正结算。普通飞行的重复接触间隔由Move按时间限制，
 *    持续切割的同刻伤害去重由SpellDamage处理，不以原版无敌帧代替。
 * 2. 伤害类型通过统一SpellDamage入口解析：数据包缺少该类型时跳过伤害，
 *    而不是抛异常崩掉服务器 tick。
 */
class Hurt(private val amount: Double = 0.0) : BaseEffect(), DirectDamage {
    override val damageStrength: Double get() = if (amount > 0.0) amount else status.damage
    override fun onHitEntity(target: Entity) {
        val dmg = if (amount > 0.0) amount else status.damage
        if (dmg <= 0.0 && projectile.damageType != work.nekow.primalspells.magic.core.SpellDamageType.HEALING) return
        val level = target.level() as? ServerLevel ?: return

        SpellDamage.hit(level, target, projectile, dmg)
    }
}
