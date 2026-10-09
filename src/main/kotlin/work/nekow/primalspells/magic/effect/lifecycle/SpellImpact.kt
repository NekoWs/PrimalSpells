package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.registry.ModDamageTypes
import kotlin.math.pow

/** 法术统一受伤与推力入口；伤害类型禁用原版固定击退，避免再叠一份无关强度的推力。 */
object SpellImpact {
    /** 5点火花弹约0.015格/刻；按有效伤害非线性增长，爆炸额外体现压力，最终有限幅。 */
    fun strength(damage: Double, blast: Boolean = false): Double =
        (0.015 * (damage.coerceAtLeast(0.0) / 5.0).pow(1.7) * if (blast) 1.8 else 1.0).coerceAtMost(1.8)

    /** 只对实际接受伤害的目标施加推力；保留原有速度，尊重击退抗性。direction为传播方向。 */
    fun hurt(level: ServerLevel, target: Entity, caster: Entity, damage: Double, direction: Vec3,
             blast: Boolean = false, knockback: Boolean = true,
             type: work.nekow.primalspells.magic.core.SpellDamageType = work.nekow.primalspells.magic.core.SpellDamageType.PROJECTILE): Boolean {
        if (!damage.isFinite() || !target.isAlive) return false
        if (type == work.nekow.primalspells.magic.core.SpellDamageType.HEALING && damage > 0.0) {
            val living = target as? LivingEntity ?: return false
            val before = living.health
            living.heal(damage.toFloat())
            return living.health > before
        }
        val settled = if (type == work.nekow.primalspells.magic.core.SpellDamageType.HEALING) -damage else damage
        if (settled <= 0.0) return false
        val source = ModDamageTypes.sourceOrNull(level, caster, type) ?: return false
        val invul = target.invulnerableTime
        target.invulnerableTime = 0
        val accepted = try { target.hurtServer(level, source, settled.toFloat()) }
            finally { target.invulnerableTime = invul }
        if (accepted && knockback && type != work.nekow.primalspells.magic.core.SpellDamageType.HEALING) {
            val resistance = if (target is LivingEntity) target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) else 0.0
            val power = strength(damage, blast) * (1.0 - resistance).coerceIn(0.0, 1.0)
            val heading = if (direction.lengthSqr() > 1e-8) direction.normalize() else Vec3(0.0, 1.0, 0.0)
            val push = if (blast) Vec3(heading.x, heading.y.coerceAtLeast(0.12), heading.z).normalize() else heading
            target.push(push.x * power, push.y * power, push.z * power)
            target.hurtMarked = true
        }
        return accepted
    }
}
