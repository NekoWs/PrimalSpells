package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.primalspells.registry.ModDamageTypes
import kotlin.math.sqrt
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.ParticleShapes

/**
 * 爆炸效果 —— 用 [MagicStatus] 的 `explosionRadius` / `explosionDamage` 触发自定义爆炸：
 * 爆炸音效 + 粒子 + 按距离衰减的伤害与击退，**不破坏方块**。
 *
 * @param onHit   碰撞（实体或方块）时触发
 * @param onDeath 投射物死亡时触发
 */
class Explosion(
    val onHit: Boolean = true,
    val onDeath: Boolean = false
) : BaseEffect() {

    override fun onHitEntity(target: Entity) {
        if (onHit) customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage)
    }

    override fun onHitBlock(pos: Vector3f) {
        if (onHit) customExplode(caster, pos, status.explosionRadius, status.explosionDamage)
    }

    override fun onDie() {
        if (onDeath) customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage)
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
        fun customExplode(source: Entity, center: Vector3f, radius: Float, damage: Double) {
            val serverLevel = source.level() as? ServerLevel ?: return
            if (radius <= 0f) return

            val cx = center.x.toDouble()
            val cy = center.y.toDouble()
            val cz = center.z.toDouble()

            // 1. 播放自定爆炸音效
            serverLevel.playSound(
                null, cx, cy, cz,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE,
                1.2f,
                0.9f + serverLevel.random.nextFloat() * 0.2f
            )

            // 2. 生成爆炸粒子效果
            spawnExplosionParticles(serverLevel, cx, cy, cz, radius)

            // 3. 实体伤害 + 击退
            if (damage > 0.0) {
                applyDamageAndKnockback(serverLevel, source, cx, cy, cz, radius, damage)
            }
        }

        /**
         * 生成爆炸粒子：中心一个大爆炸 + 球体内均匀分布的小爆炸粒子（数量与半径平方成正比）。
         */
        private fun spawnExplosionParticles(level: ServerLevel, x: Double, y: Double, z: Double, r: Float) {
            val center = Vec3(x, y, z)
            // 大爆炸核心粒子
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)

            val particleCount = (r * r * 30f).toInt().coerceIn(20, 400)
            ParticleShapes.sphereVolume(level, center, r.toDouble(), particleCount, ParticleTypes.EXPLOSION, speed = 0.05)
        }

        /**
         * 对半径内实体施加伤害与击退
         *
         * 伤害衰减：实际伤害 = 中心伤害 × (1 - 距离/半径)
         * 击退方向：从爆炸中心向外
         *
         * @param level  服务端世界
         * @param source 爆炸来源（不会被伤害）
         * @param cx, cy, cz 爆炸中心
         * @param r      爆炸半径
         * @param damage 中心最大伤害
         */
        private fun applyDamageAndKnockback(
            level: ServerLevel,
            source: Entity,
            cx: Double, cy: Double, cz: Double,
            r: Float,
            damage: Double
        ) {
            val center = Vec3(cx, cy, cz)
            val aabb = AABB.ofSize(center, r.toDouble() * 2.0, r.toDouble() * 2.0, r.toDouble() * 2.0)

            level.getEntities(null, aabb) { it != source && it.isAlive }.forEach { entity ->
                val dx = entity.x - cx
                val dy = entity.y + entity.eyeHeight / 2.0 - cy  // 以实体中心为准
                val dz = entity.z - cz
                val dist = sqrt(dx * dx + dy * dy + dz * dz).toFloat()
                if (dist > r) return@forEach

                // 距离衰减伤害
                val falloff = (1.0 - dist / r).coerceAtLeast(0.0)
                val actualDamage = damage * falloff

                // 伤害（伤害类型缺失时跳过本次伤害，而不是崩服务器 tick）
                if (actualDamage > 0.0) {
                    val damageSource = ModDamageTypes.projectileSourceOrNull(level, source)
                    if (damageSource != null) {
                        val invul = entity.invulnerableTime
                        entity.invulnerableTime = 0
                        entity.hurtServer(level, damageSource, actualDamage.toFloat())
                        entity.invulnerableTime = invul
                    }
                }

                // 击退：从中心向外, 力度与伤害成正比
                if (dist > 0.01f) {
                    val knockbackStrength = (falloff * 1.5).coerceAtMost(3.0)
                    val kbX = dx / dist * knockbackStrength
                    val kbZ = dz / dist * knockbackStrength
                    val kbY = (dy / dist * knockbackStrength).coerceAtLeast(0.3)  // 最小向上击退

                    entity.push(kbX, kbY, kbZ)
                    entity.hurtMarked = true
                }
            }
        }
    }
}
