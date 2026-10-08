package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 命中实体造成伤害。
 *
 * @param amount 固定伤害；`<= 0` 时改用投射物当前的 `status.damage`（可被伤害类修饰器改写，
 *               也可以被 `Piercing` 这类修饰器压到 0）
 *
 * 两个刻意的实现细节：
 * 1. 结算前把目标的 `invulnerableTime` 临时清零再恢复 —— 这样**同一 tick 的多次命中**与
 *    **每 tick 的持续命中（穿刺）**都能真正结算，是穿刺玩法的依赖点。
 * 2. 伤害类型通过 [ModDamageTypes.projectileSourceOrNull] 解析：数据包缺少该类型时跳过伤害，
 *    而不是抛异常崩掉服务器 tick。
 */
class Hurt(private val amount: Double = 0.0) : BaseEffect(), DirectDamage {
    override val damageStrength: Double get() = if (amount > 0.0) amount else status.damage
    override fun onHitEntity(target: Entity) {
        val dmg = if (amount > 0.0) amount else status.damage
        if (dmg <= 0.0) return
        val level = target.level() as? ServerLevel ?: return

        SpellDamage.hit(level, target, projectile, dmg)
    }
}
