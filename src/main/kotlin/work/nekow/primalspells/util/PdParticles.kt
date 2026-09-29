package work.nekow.primalspells.util

import net.minecraft.server.level.ServerLevel
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.wand.Wand

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
