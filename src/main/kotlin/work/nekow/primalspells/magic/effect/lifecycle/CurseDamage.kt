package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.registry.ModDamageTypes

/** 生命付费走有来源的原版死亡流程；伤害标签与事件保证不被护甲、抗性或吸收抵扣。 */
object CurseDamage {
    fun pay(caster: Entity, amount: Float) {
        val living = caster as? LivingEntity ?: return
        val level = living.level() as? ServerLevel ?: return
        if (amount <= 0f || !living.isAlive) return
        val source = ModDamageTypes.sourceOrNull(level, caster, SpellDamageType.CURSE) ?: return
        val invulnerable = living.invulnerableTime
        living.invulnerableTime = 0
        try { living.hurtServer(level, source, amount) } finally { living.invulnerableTime = invulnerable }
    }
}
