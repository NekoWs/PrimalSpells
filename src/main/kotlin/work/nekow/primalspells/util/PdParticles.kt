package work.nekow.primalspells.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.wand.Wand

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
 * 生成一个 PD 粒子 —— 本模组「生成一颗粒子」的唯一写法。
 *
 * 本模组只决定**数值**（位置/初速/颜色/尺寸/寿命），生成、同步、渲染全归 ParticleDrawing；
 * 这里**不做任何簿记**：成员归 `ParticleBatch` 持有，寿命到点由 PD 自己回收。
 *
 * @param lifetimeTicks 寿命（tick）；[PD_IMMORTAL] = 永存
 * @param velocity      初速度（blocks/tick）。非 null 时多下发一次速度包（只在生成时一次，
 *                      之后位置由 PD 逐 tick 积分）—— 这条路径**零逐 tick 包**
 * @return 句柄；被维度粒子上限拒绝时为 `null`（调用方按「下一 tick 再补」处理，不要空转）
 */
fun ParticleManager.spawnParticle(
    pos: Vec3,
    color: Color,
    scale: Float,
    lifetimeTicks: Int = PD_IMMORTAL,
    velocity: Vec3? = null,
): ParticleHandle? {
    val handle = create().position(pos).color(color).scale(scale).lifetime(lifetimeTicks).spawn() ?: return null
    if (velocity != null) handle.setVelocity(velocity)
    return handle
}
