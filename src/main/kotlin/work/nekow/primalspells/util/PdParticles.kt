package work.nekow.primalspells.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.CurveKey
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleSpawnSpec
import work.nekow.particledrawing.api.ParticleVisual
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.wand.Wand

/** 法术片元保持全亮但不照亮环境；光照卡和火把必须显式申请光源。 */
fun spellVisual(): ParticleVisual = ParticleVisual().glowing(true).lightLevel(0)

/** 单颗生成也显式同步全亮/零光源标志，避免只设置visual漏掉客户端索引。 */
fun ParticleManager.spellParticle(): ParticleHandle.Builder = create().glowing(true).lightLevel(0)

/** 批量生成与单颗使用相同的默认光照契约。 */
fun spellParticleSpec(): ParticleSpawnSpec = ParticleSpawnSpec().glowing(true).lightLevel(0)

/** PD 粒子寿命：`-1` = 永存（由调用方在明确时机销毁，如 `ParticleBatch.clear()`）。 */
const val PD_IMMORTAL = -1

/**
 * 取该法杖在当前维度可用的 ParticleDrawing 粒子管理器；尚未创建时现场建立并缓存回 `wand.status`。
 *
 * 为什么要兜底：管理器原本只在 `Wand.tick(caster)`（持有者背包物品被 tick）里建立，
 * 而**未来的生物施法者不走 `inventoryTick`** —— 不兜底就会出现「法术有伤害但没有粒子」这种半残表现。
 * 维度变化时（跨维度传送）也会重新建立，避免把粒子发到旧维度。
 */
fun Wand.pdParticles(level: ServerLevel): ParticleManager {
    val current = status.particleManager
    if (current != null && current.level == level) return current
    return ParticleManager.of(level).also { status.particleManager = it }
}

/**
 * 带速度/加速度初值的便捷生成入口；默认全亮、零动态光源。
 *
 * 本模组只决定**数值**（位置/初速/颜色/尺寸/寿命），生成、同步、渲染全归 ParticleDrawing；
 * 这里**不做任何簿记**：成员归 `ParticleBatch` 持有，寿命到点由 PD 自己回收。
 *
 * @param lifetimeTicks 寿命（tick）；[PD_IMMORTAL] = 永存
 * @param velocity      初速度（blocks/tick）。非 null 时多下发一次速度包（只在生成时一次，
 *                      之后位置由 PD 逐 tick 积分）—— 这条路径**零逐 tick 包**
 * @param acceleration  恒定加速度（blocks/tick²）。非 null 时多下发一次施力包；之后**两端逐 tick
 *                      自行积分**（速度累加、位置跟上），同样**零逐 tick 包** ——
 *                      「一直受同一个力」的粒子（重力场的下坠）用这个，别自己每 tick 重算速度
 * @param visual        可选的贴图、朝向与混合规格，在生成时一次下发。
 * @return 句柄；被维度粒子上限拒绝时为 `null`（调用方按「下一 tick 再补」处理，不要空转）
 */
fun ParticleManager.spawnParticle(
    pos: Vec3,
    color: Color,
    scale: Float,
    lifetimeTicks: Int = PD_IMMORTAL,
    velocity: Vec3? = null,
    acceleration: Vec3? = null,
    visual: ParticleVisual? = null,
): ParticleHandle? {
    val builder = spellParticle().position(pos).color(color).scale(scale).lifetime(lifetimeTicks)
    if (visual != null) builder.visual(visual).glowing(visual.glowing).lightLevel(visual.lightLevel)
    if (lifetimeTicks > 1) {
        val end = lifetimeTicks.toDouble()
        builder.sizeCurve(CurveKey.at(0, 0), CurveKey.at(kotlin.math.min(1.5, end * 0.2), 1, EasingType.EASE_OUT),
            CurveKey.at(kotlin.math.max(end * 0.35, end - 5), 1), CurveKey.at(end, 0, EasingType.EASE_IN_OUT))
    }
    val handle = builder.spawn() ?: return null
    if (velocity != null) handle.setVelocity(velocity)
    // 力与速度**叠加**（PD 明确语义：施力不覆盖已有速度），所以「初速 + 恒定加速度」可以同时给
    if (acceleration != null) handle.applyForce(acceleration, FORCE_FOREVER)
    return handle
}

/** 施加无限持续的力（PD `applyForce` 的 `ticks` 约定：负数 = 无限，直到被下一次指令覆盖）。 */
private const val FORCE_FOREVER = -1
