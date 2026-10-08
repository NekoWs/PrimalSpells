package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import work.nekow.primalspells.magic.core.ShieldGuard
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModDamageTypes

/** 服务端直接命中结算；普通弹、电弧和光束共用护盾、无敌帧及冰冻接入。 */
object SpellDamage {
    /** 返回实际伤害是否被目标接受；被盾挡下时不施加冰冻。 */
    fun hit(level: ServerLevel, target: Entity, shot: Projectile, amount: Double): Boolean {
        if (amount <= 0 || !target.isAlive) return false
        if (target is LivingEntity && ShieldGuard.absorbProjectile(level, target, shot)) return false
        val source = ModDamageTypes.projectileSourceOrNull(level, shot.caster) ?: return false
        val bonus = if (target is LivingEntity && amount >= 4.0 && FrozenStatus.isFrozen(target)) 2.0 else 0.0
        val invul = target.invulnerableTime
        target.invulnerableTime = 0
        val accepted = try { target.hurtServer(level, source, (amount + bonus).toFloat()) }
            finally { target.invulnerableTime = invul }
        if (accepted && target is LivingEntity) {
            if (bonus > 0) FrozenStatus.shatter(target)
            if (shot.status.freezeTicks > 0) FrozenStatus.freeze(target, shot.status.freezeTicks)
        }
        if (accepted && "mana_injection" in shot.modifiers) {
            shot.wand.status.mana = kotlin.math.min(shot.wand.mana, shot.wand.status.mana + 2.0 * (shot.modifiers["mana_injection"] ?: 1))
        }
        return accepted
    }
}
