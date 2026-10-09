package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.pdParticles
import kotlin.math.sqrt

/**
 * 区域场效果基类 —— 黑洞与重力场共用的「持续区域」实现。
 *
 * **每 tick 结算一次**（区域魔法要即时响应）：
 * 1. 视觉：[spawnVisuals] / [updateVisuals] 由子类用 ParticleDrawing 粒子实现（本类不碰渲染与同步）；
 * 2. 生物：半径内每 tick 刷新迟缓（等级 [slownessAmplifier]），并按 [pullPerTick] 拉向中心；
 * 3. **魔法投射物**：半径内的投射物每 tick 调用一次 [applyProjectileForce] 施力
 *    —— 黑洞把投射物吸向中心、重力场给投射物持续向下的力，都靠这个钩子。
 *
 * **离开即解除**：记录自己影响过的生物，一旦离开范围（或死亡/换维度）就**立刻**撤销迟缓；
 * 效果结束（[onDie]）时对全部受影响生物与自己的 PD 粒子做同样的清理。
 * 撤销迟缓时只移除「等级与本效果一致」的，避免连带清掉药水等其它来源的效果。
 *
 * @param radius            生效半径（方块）
 * @param slownessAmplifier 迟缓等级：0 = 迟缓 I，3 = 迟缓 IV
 * @param pullPerTick       对**生物**每 tick 的向心加速度系数；0 表示不拉拽
 */
abstract class AreaFieldEffect(
    private val radius: Double = 5.0,
    private val slownessAmplifier: Int = 0,
    private val pullPerTick: Double = 0.0,
) : BaseEffect() {

    // 区域场读取最终位置，且不改自身速度；必须在 Move 与轨道 OVERRIDE 之后结算。
    final override val phase = EffectPhase.VISUAL

    /** 本效果当前施加过迟缓的生物（用于「离开即解除」）。 */
    private val affected = HashSet<LivingEntity>()

    /**
     * 本效果所在的维度：**首 tick 捕获**，之后不再看 `caster.level()`。
     *
     * 施法者跨维度后 `caster.level()` 会变成新维度，而本效果的坐标（`status.pos`）仍在原维度
     * —— 继续用新维度会让迟缓、投射物施力与 PD 粒子一起落到错误维度并**静默失效**
     * （查询不到任何实体）。固定下来后，「一个区域场 = 一个维度」也才成立，
     * 子类缓存粒子集（构造时绑定该维度的粒子管理器）才是安全的。
     */
    private var fieldLevel: ServerLevel? = null

    /**
     * 本效果使用的 PD 粒子管理器：与 [fieldLevel] 一起在**首 tick 捕获**。
     *
     * 为什么要缓存：效果的**退场动画**（[clearVisuals]）在 `onDie` 里触发，此时可能已经拿不到
     * 合适的 `caster.level()`（施法者跨维度/卸载）；粒子必须交给「当初铺它们的那套管理器」所在维度去清。
     */
    private var fieldManager: ParticleManager? = null

    // ---------- 子类扩展点 ----------

    /** 每 tick 生成本 tick 的视觉粒子（用 PD API；默认无）。 */
    protected open fun spawnVisuals(manager: ParticleManager, center: Vec3) { }

    /** 每 tick 更新已有视觉粒子（施加力 / 清理过期；默认无）。 */
    protected open fun updateVisuals(manager: ParticleManager, center: Vec3) { }

    /**
     * 效果结束：处理（销毁 / 播放退场动画）本效果生成的全部 PD 粒子（默认无）。
     *
     * @param manager 当初铺粒子的管理器（= 效果所在维度的管理器）
     * @param center  区域中心（= `status.pos`）
     */
    protected open fun clearVisuals(manager: ParticleManager, center: Vec3) { }

    /**
     * 对半径内的**魔法投射物**每 tick 施加一次力（默认无）。
     *
     * @param dirToCenter 从投射物指向区域中心的单位方向
     * @param dist        投射物到中心的距离（方块）
     */
    protected open fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) { }

    protected open fun affectsEntity(entity: LivingEntity): Boolean = true

    /** 区域内生物结算的扩展点；读取最终场心与实体中心距离，例如黑洞暗核持续伤害。 */
    protected open fun affectEntity(level: ServerLevel, entity: LivingEntity, center: Vec3, dist: Double) { }

    // ---------- 主循环 ----------

    override fun onTick() {
        val level = fieldLevel ?: (caster.level() as? ServerLevel)?.also { fieldLevel = it } ?: return
        val center = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())

        // 视觉：PD 粒子（管理器缺失时现场建立，保证非玩家施法者也有粒子表现）
        val manager = wand.pdParticles(level)
        fieldManager = manager
        spawnVisuals(manager, center)
        updateVisuals(manager, center)

        applyProjectileForces(center)

        val aabb = AABB.ofSize(center, radius * projectile.areaScale * 2.0, radius * projectile.areaScale * 2.0, radius * projectile.areaScale * 2.0)
        val inRange = level.getEntitiesOfClass(LivingEntity::class.java, aabb) {
            it.isAlive && affectsEntity(it) && it.boundingBox.center.distanceToSqr(center) <= radius * radius * projectile.areaScale * projectile.areaScale
        }

        // 1) 先处理离开范围的生物：立刻撤销迟缓（不等效果自然到期）
        val iterator = affected.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            val left = !entity.isAlive || entity.level() !== level || entity !in inRange
            if (left) {
                removeOurSlowness(entity)
                iterator.remove()
            }
        }

        // 2) 再对当前范围内的生物施加迟缓/拉拽
        for (entity in inRange) {
            entity.addEffect(
                MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_DURATION, slownessAmplifier, false, false),
                caster,
            )
            affected.add(entity)
            if (pullPerTick > 0.0) {
                val dist = entity.boundingBox.center.distanceTo(center)
                val dir = center.subtract(entity.boundingBox.center).normalize()
                // 距离越近吸力越强（每 tick 施加一次，故强度按 tick 计）
                entity.deltaMovement = entity.deltaMovement.scale(0.96).add(dir.scale(pullPerTick / ((dist + 0.5) * work.nekow.primalspells.magic.core.MagicMass.of(entity))))
                entity.hurtMarked = true
            }
            affectEntity(level, entity, center, entity.boundingBox.center.distanceTo(center))
        }
    }

    /**
     * 对半径内的魔法投射物施力。
     *
     * 剪枝：场上没有投射物直接返回；先按**距离平方**判断是否进范围，再算方向与距离。
     * 排除本效果自己所在的投射物 —— 否则区域弹会被自己往下压、或被自己吸住。
     */
    private fun applyProjectileForces(center: Vec3) {
        val projectiles = MagicManager.projectiles
        if (projectiles.isEmpty()) return
        val radiusSq = radius * radius * projectile.areaScale * projectile.areaScale
        for (p in projectiles) {
            if (p === projectile || !p.alive || p.awaitingRelease || p.caster.level() !== fieldLevel) continue
            val dx = center.x - p.status.pos.x.toDouble()
            val dy = center.y - p.status.pos.y.toDouble()
            val dz = center.z - p.status.pos.z.toDouble()
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > radiusSq) continue
            val dist = sqrt(distSq)
            if (dist < 1e-4) continue
            val dir = Vector3f((dx / dist).toFloat(), (dy / dist).toFloat(), (dz / dist).toFloat())
            applyProjectileForce(p, dir, dist)
        }
    }

    /** 区域结束（投射物死亡）：撤销全部迟缓并清理粒子。 */
    override fun onDie() {
        for (entity in affected) removeOurSlowness(entity)
        affected.clear()
        // 退场动画/清理交给「当初铺粒子的那套管理器」；一次都没铺过（首 tick 就没跑起来）则无需处理
        fieldManager?.let { manager ->
            clearVisuals(manager, Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()))
        }
    }

    /** 仅当生物当前的迟缓等级与本效果一致时才移除，避免误清其它来源（药水等）的迟缓。 */
    private fun removeOurSlowness(entity: LivingEntity) {
        val current = entity.getEffect(MobEffects.SLOWNESS) ?: return
        if (current.amplifier == slownessAmplifier) entity.removeEffect(MobEffects.SLOWNESS)
    }

    private companion object {
        /** 每 tick 刷新，持续时间只需覆盖到下个 tick（留点余量避免闪烁）。 */
        const val SLOWNESS_DURATION = 5
    }
}
