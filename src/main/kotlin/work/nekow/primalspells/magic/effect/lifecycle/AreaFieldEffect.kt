package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.ParticleShapes

/**
 * 区域场效果基类 —— 黑洞与重力场共用的「持续区域」实现。
 *
 * **每 tick 结算一次**（区域魔法要即时响应，不做间隔刷新）：
 * - 对半径 [radius] 内的存活生物每 tick 刷新迟缓（等级 [slownessAmplifier]）；
 * - 每 tick 按 [pullPerTick] 施加一次向心加速度（`0` = 只减速不拉拽）；
 * - 每 tick 在半径 [particleRadius] 的球壳上撒 [particleCount] 个 [particle] 粒子（纯视觉）。
 *
 * **离开即解除**：本效果记录自己影响过的实体，实体一旦离开范围（或死亡/换维度）就**立刻**
 * 撤销迟缓，而不是等效果自然到期；效果结束（[onDie]）时对全部受影响实体做同样清理。
 * 撤销时只移除「等级与本效果一致」的迟缓，避免连带清掉药水等其它来源的效果。
 *
 * @param radius            生效半径（方块）
 * @param slownessAmplifier 迟缓等级：0 = 迟缓 I，3 = 迟缓 IV
 * @param pullPerTick       每 tick 的向心加速度系数；0 表示不拉拽
 * @param particle          球面粒子类型
 * @param particleCount     每 tick 粒子数量
 * @param particleRadius    粒子球壳半径（可小于生效半径，视觉上更收敛）
 */
abstract class AreaFieldEffect(
    private val radius: Double = 5.0,
    private val slownessAmplifier: Int = 0,
    private val pullPerTick: Double = 0.0,
    private val particle: ParticleOptions,
    private val particleCount: Int = 12,
    private val particleRadius: Double = 2.5,
) : BaseEffect() {

    /** 本效果当前施加过迟缓的实体（用于「离开即解除」）。 */
    private val affected = HashSet<LivingEntity>()

    /** 子类可覆写：对范围内每个活体追加结算（在迟缓与拉拽之后调用）。 */
    protected open fun affect(level: ServerLevel, entity: LivingEntity, center: Vec3) { }

    override fun onTick() {
        val level = caster.level() as? ServerLevel ?: return
        val center = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())

        ParticleShapes.sphereSurface(level, center, particleRadius, particleCount, particle)

        val aabb = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0)
        val inRange = level.getEntitiesOfClass(LivingEntity::class.java, aabb) { it.isAlive }

        // 1) 先处理离开范围的实体：立刻撤销迟缓（不等效果自然到期）
        val iterator = affected.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            val left = !entity.isAlive || entity.level() !== level || entity !in inRange
            if (left) {
                removeOurSlowness(entity)
                iterator.remove()
            }
        }

        // 2) 再对当前范围内的实体施加迟缓/拉拽
        for (entity in inRange) {
            entity.addEffect(
                MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_DURATION, slownessAmplifier, false, false),
                caster,
            )
            affected.add(entity)
            if (pullPerTick > 0.0) {
                val dist = entity.position().distanceTo(center)
                val dir = center.subtract(entity.position()).normalize()
                // 距离越近吸力越强（每 tick 施加一次，故强度按 tick 计）
                entity.addDeltaMovement(dir.scale(pullPerTick / (dist + 0.5)))
            }
            affect(level, entity, center)
        }
    }

    /** 区域结束（投射物死亡）：把所有受影响实体上的迟缓一并撤销。 */
    override fun onDie() {
        for (entity in affected) removeOurSlowness(entity)
        affected.clear()
    }

    /** 仅当实体当前的迟缓等级与本效果一致时才移除，避免误清其它来源（药水等）的迟缓。 */
    private fun removeOurSlowness(entity: LivingEntity) {
        val current = entity.getEffect(MobEffects.SLOWNESS) ?: return
        if (current.amplifier == slownessAmplifier) entity.removeEffect(MobEffects.SLOWNESS)
    }

    private companion object {
        /** 每 tick 刷新，持续时间只需覆盖到下个 tick（留点余量避免闪烁）。 */
        const val SLOWNESS_DURATION = 5
    }
}
