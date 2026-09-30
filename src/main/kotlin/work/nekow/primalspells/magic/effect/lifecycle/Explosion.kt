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
import work.nekow.primalspells.registry.ModDamageTypes
import kotlin.math.sqrt
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Deferred

/**
 * 爆炸效果 —— 用 [MagicStatus] 的 `explosionRadius` / `explosionDamage` 触发自定义爆炸：
 * **蓄力（内爆）→ 爆发**，按距离衰减的伤害与击退，**不破坏方块**。
 *
 * 第 28 轮起爆炸分两拍（用户口径）：
 * 1. [ExplosionFx.implode]：黑色核心球（爆炸半径的 1/3）+ 蓄力线条 + 向心聚拢粒子，持续 [chargeTicks]；
 * 2. 蓄力结束才结算：音效 + [ExplosionFx.burst]（球体放大到爆炸半径 + 向外飞散）+ 伤害与击退。
 *
 * 也就是说**爆炸现在是可以躲的**：伤害被推迟到蓄力结束（伤害时机与视觉一致）。
 * 原版爆炸粒子（`ParticleTypes.EXPLOSION*`）已全部删除。
 *
 * @param onHit   碰撞（实体或方块）时开始蓄力
 * @param onDeath 投射物死亡时开始蓄力
 * @param chargeTicks 蓄力时长（tick）；<= 0 = 按半径自动推（[ExplosionFx.autoChargeTicks]）
 */
class Explosion(
    val onHit: Boolean = true,
    val onDeath: Boolean = false,
    private val chargeTicks: Int = 0,
) : BaseEffect() {

    /** 同一发弹可能既触发 `onHit` 又触发 `onDie`：一次爆炸只安排一次蓄力（否则会打两次伤害） */
    private var scheduled = false

    override fun onHitEntity(target: Entity) {
        if (onHit) startCharge(Vector3f(status.pos))
    }

    override fun onHitBlock(pos: Vector3f) {
        if (onHit) startCharge(Vector3f(pos))
    }

    override fun onDie() {
        if (onDeath) startCharge(Vector3f(status.pos))
    }

    /**
     * 开始蓄力，并把「蓄力结束的结算」排进 [Deferred]（服务端延迟队列，由 `MagicManager` 每 tick 推）。
     *
     * 位置要**冻结**一份：`status.pos` 是会继续被投射物改写的活对象，
     * 直接捕获它会让爆炸中心在蓄力期间跟着弹走。
     */
    private fun startCharge(center: Vector3f) {
        if (scheduled) return
        scheduled = true
        val level = caster.level() as? ServerLevel ?: return
        val radius = status.explosionRadius
        if (radius <= 0f) return
        val damage = status.explosionDamage
        val ticks = if (chargeTicks > 0) chargeTicks else ExplosionFx.autoChargeTicks(radius)
        val frozen = Vector3f(center)
        ExplosionFx.implode(
            ParticleManager.of(level),
            Vec3(frozen.x.toDouble(), frozen.y.toDouble(), frozen.z.toDouble()),
            radius,
            ticks,
        )
        Deferred.after(level, ticks) { customExplode(caster, frozen, radius, damage) }
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

            // 2. 爆发表现：球体放大到爆炸半径 + 向外飞散的粒子（原版爆炸粒子已删除）
            ExplosionFx.burst(ParticleManager.of(serverLevel), Vec3(cx, cy, cz), radius)

            // 3. 实体伤害 + 击退
            if (damage > 0.0) {
                applyDamageAndKnockback(serverLevel, source, cx, cy, cz, radius, damage)
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

                // 能量护盾：壳外侧的爆炸可以替持有者吃掉这一份（伤害与击退一起免掉 ——
                // 抵扣必须在施加伤害之前做，见 ShieldGuard 的 KDoc）
                if (entity is LivingEntity &&
                    ShieldGuard.absorbExplosion(level, entity, center, r, actualDamage)
                ) {
                    return@forEach
                }

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
