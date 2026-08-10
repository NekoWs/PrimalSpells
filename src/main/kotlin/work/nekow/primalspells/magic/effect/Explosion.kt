package work.nekow.primalspells.magic.effect

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.primalspells.ModDamageTypes
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 爆炸效果 —— 使用 [MagicStatus] 爆炸参数触发自定义爆炸（非原版）。
 *
 * @param onHit   碰撞（实体或方块）时触发
 * @param onDeath 投射物死亡时触发
 */
class Explosion(
    val onHit: Boolean = true,
    val onDeath: Boolean = false
) : BaseEffect() {

    override fun onHitEntity(target: Entity) {
        if (onHit) customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage, status.explosionLevel)
    }

    override fun onHitBlock(pos: Vector3f) {
        if (onHit) customExplode(caster, pos, status.explosionRadius, status.explosionDamage, status.explosionLevel)
    }

    override fun onDie() {
        if (onDeath) customExplode(caster, Vector3f(status.pos), status.explosionRadius, status.explosionDamage, status.explosionLevel)
    }

    companion object {

        /**
         * 自定义爆炸方法 —— 所有爆炸相关类均应通过此方法执行爆炸。
         *
         * @param source  爆炸来源实体（施法者）
         * @param center  爆炸中心坐标
         * @param radius  爆炸半径（方块数）
         * @param damage  中心最大伤害（距离衰减至边缘为 0）
         * @param level   爆炸等级（0 = 无方块破坏, >=1 = 破坏方塊）
         */
        fun customExplode(source: Entity, center: Vector3f, radius: Float, damage: Double, level: Int) {
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

            // 3. 破坏方块（若爆炸等级允许）
            if (level >= 1) {
                destroyBlocks(serverLevel, source, cx, cy, cz, radius)
            }

            // 4. 实体伤害 + 击退
            if (damage > 0.0) {
                applyDamageAndKnockback(serverLevel, source, cx, cy, cz, radius, damage)
            }
        }

        /**
         * 生成爆炸粒子
         *
         * @param level  服务端世界
         * @param x, y, z 爆炸中心
         * @param r      爆炸半径
         */
        private fun spawnExplosionParticles(level: ServerLevel, x: Double, y: Double, z: Double, r: Float) {
            // 大爆炸核心粒子
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)

            // 球面分布的小爆炸粒子，数量与半径成正比
            val particleCount = (r * r * 30f).toInt().coerceIn(20, 400)
            for (i in 0 until particleCount) {
                // 球体内均匀随机采样
                val theta = level.random.nextDouble() * Math.PI * 2.0
                val phi = acos(2.0 * level.random.nextDouble() - 1.0)
                val dist = level.random.nextDouble() * r
                val px = x + sin(phi) * cos(theta) * dist
                val py = y + sin(phi) * sin(theta) * dist
                val pz = z + cos(phi) * dist

                level.sendParticles(
                    ParticleTypes.EXPLOSION,
                    px, py, pz,
                    1,
                    0.0, 0.0, 0.0,
                    0.05
                )
            }
        }

        /**
         * 破坏方块 —— 破坏爆炸半径内的随机方块
         *
         * @param r 爆炸半径
         */
        private fun destroyBlocks(level: ServerLevel, source: Entity, x: Double, y: Double, z: Double, r: Float) {
            val intR = r.toInt().coerceAtLeast(1)
            val maxBlocks = (r * r * 8f).toInt().coerceIn(5, 120)

            var destroyed = 0
            // 采样随机方块位置进行破坏
            for (attempt in 0 until maxBlocks * 3) {
                if (destroyed >= maxBlocks) break

                val bx = (x + (level.random.nextDouble() - 0.5) * r * 2.0).toInt()
                val by = (y + (level.random.nextDouble() - 0.5) * r * 2.0).toInt()
                val bz = (z + (level.random.nextDouble() - 0.5) * r * 2.0).toInt()
                val pos = BlockPos(bx, by, bz)
                val state = level.getBlockState(pos)

                // 检查方块是否在球体范围内、非空气
                val dx = bx - x; val dy = by - y; val dz = bz - z
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq > r * r) continue
                if (state.isAir) continue

                level.destroyBlock(pos, false, source)
                destroyed++
            }
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

                // 伤害
                if (actualDamage > 0.0) {
                    val damageType = level.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(ModDamageTypes.PROJECTILE_KEY)
                    val damageSource = DamageSource(damageType, source)

                    val invul = entity.invulnerableTime
                    entity.invulnerableTime = 0
                    entity.hurtServer(level, damageSource, actualDamage.toFloat())
                    entity.invulnerableTime = invul
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
