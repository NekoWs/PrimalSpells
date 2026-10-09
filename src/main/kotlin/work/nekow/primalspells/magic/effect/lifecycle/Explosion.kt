package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.ShieldGuard
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 爆炸效果 —— 用 [MagicStatus] 的 `explosionRadius` / `explosionDamage` 触发自定义爆炸：
 * **触发即爆**（音效 + [ExplosionFx.burst] 粒子 + 按距离衰减的伤害与击退），**不破坏方块**。
 *
 * 第 28~31 轮曾有过「先内爆蓄力、蓄力结束再结算」的版本；**第 32 轮按用户口径整体移除**
 * （「把爆炸的蓄力阶段给移除掉，直接爆」）—— 表现层只保留爆发那一拍。
 *
 * @param onHit   碰撞（实体或方块）时爆炸
 * @param onDeath 投射物死亡时爆炸
 */
class Explosion(
    val onHit: Boolean = true,
    val onDeath: Boolean = false,
    val bounceOnly: Boolean = false
) : BaseEffect() {
    private var terminalHit = false

    override fun onHitEntity(target: Entity) {
        if (onHit && !bounceOnly) customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage, shot = projectile)
        if (onHit && !projectile.alive) terminalHit = true
    }

    override fun onHitBlock(pos: Vector3f) {
        if (onHit && (!bounceOnly || projectile.alive)) customExplode(caster, pos, status.explosionRadius, status.explosionDamage, shot = projectile)
        if (onHit && !projectile.alive) terminalHit = true
    }

    override fun onDie() {
        if (onDeath && !terminalHit) customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage, shot = projectile)
    }

    companion object {

        /**
         * 自定义爆炸的唯一入口 —— 所有爆炸相关效果都通过它执行。
         *
         * @param source 爆炸来源实体（施法者）
         * @param center 爆炸中心坐标
         * @param radius 爆炸半径（方块数；<= 0 直接返回）
         * @param damage 中心最大伤害（线性衰减到边缘为 0）
         */
        fun customExplode(source: Entity, center: Vector3f, radius: Float, damage: Double, level: ServerLevel? = null, shot: work.nekow.primalspells.magic.projectile.Projectile? = null) {
            val serverLevel = level ?: (source.level() as? ServerLevel) ?: return
            if (radius <= 0f) return

            val cx = center.x.toDouble()
            val cy = center.y.toDouble()
            val cz = center.z.toDouble()

            CrystalReaction.notifyExplosion(serverLevel, Vec3(cx, cy, cz), radius.toDouble())

            SpellInteractions.shock(serverLevel, Vec3(cx, cy, cz), radius.toDouble(), damage, work.nekow.primalspells.magic.core.MagicManager.projectiles.toList())

            // 1. 播放自定爆炸音效
            serverLevel.playSound(
                null, cx, cy, cz,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE,
                1.2f,
                0.9f + serverLevel.random.nextFloat() * 0.2f
            )

            // 2. 爆发表现（原版爆炸粒子已删除；第 32 轮起没有蓄力，触发即爆）
            ExplosionFx.burst(ParticleManager.of(serverLevel), Vec3(cx, cy, cz), radius, shot?.id ?: "explosion")

            // 3. 实体伤害 + 击退
            if (damage > 0.0) {
                applyDamageAndKnockback(serverLevel, source, cx, cy, cz, radius, damage, shot)
            }
        }

        /**
         * 对半径内实体施加伤害与击退
         *
         * 伤害衰减：实际伤害 = 中心伤害 × (1 - 距离/半径)
         * 击退方向：从爆炸中心向外
         *
         * @param level  服务端世界
         * @param source 爆炸来源（也可能受到自伤）
         * @param cx, cy, cz 爆炸中心
         * @param r      爆炸半径
         * @param damage 中心最大伤害
         */
        private fun applyDamageAndKnockback(
            level: ServerLevel,
            source: Entity,
            cx: Double, cy: Double, cz: Double,
            r: Float,
            damage: Double,
            shot: work.nekow.primalspells.magic.projectile.Projectile?
        ) {
            val center = Vec3(cx, cy, cz)
            val aabb = AABB.ofSize(center, r.toDouble() * 2.0, r.toDouble() * 2.0, r.toDouble() * 2.0)

            level.getEntities(null, aabb) { it.isAlive }.forEach { entity ->
                val direction = entity.boundingBox.center.subtract(center)
                val dist = direction.length().toFloat()
                if (dist > r) return@forEach

                // 距离衰减伤害
                val falloff = (1.0 - dist / r).coerceAtLeast(0.0)
                val frozen = entity is LivingEntity && damage * falloff >= 4.0 && FrozenStatus.isFrozen(entity)
                val actualDamage = damage * falloff + if (frozen) 2.0 else 0.0

                // 能量护盾：壳外侧的爆炸可以替持有者吃掉这一份（伤害与击退一起免掉 ——
                // 抵扣必须在施加伤害之前做，见 ShieldGuard 的 KDoc）
                if (entity is LivingEntity &&
                    ShieldGuard.absorbExplosion(level, entity, center, r, actualDamage)
                ) {
                    return@forEach
                }

                // 爆炸一律允许自伤，统一入口抑制原版固定击退，再按实际衰减伤害推离爆心。
                val accepted = SpellImpact.hurt(level, entity, source, actualDamage, direction, blast = true, type = work.nekow.primalspells.magic.core.SpellDamageType.EXPLOSION)
                if (accepted && frozen) FrozenStatus.shatter(entity)
                if (accepted && entity is LivingEntity && shot != null && shot.status.freezeTicks > 0)
                    FrozenStatus.freeze(entity, shot.status.freezeTicks)
            }
        }
    }
}
