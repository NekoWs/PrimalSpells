package work.nekow.primalspells.magic.effect

import net.minecraft.resources.Identifier
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle

/**
 * 投射物本体粒子：表示投射物当前所在位置的一个粒子，每 tick 跟随 [status.pos] 移动。
 *
 * 与 [Trajectory]（尾迹）不同，本体粒子只保留一个（永存，死亡时移除），
 * 始终贴在投射物当前位置，作为「投射物现在在哪」的视觉标记。
 *
 * @param color   本体粒子颜色
 * @param texture 本体粒子贴图接口（预留）：当前程序化粒子渲染为纯色方块，
 *                贴图画好后在此接入。
 * @param scale   本体粒子缩放
 */
class ProjectileBody(
    val color: Color,
    val texture: Identifier? = null,
    val scale: Float = 1f,
) : BaseEffect() {

    private var handle: ParticleHandle? = null

    override fun onTick() {
        ensureSpawned()
        val h = handle ?: return
        val pos = status.pos
        // 直设位置 + 客户端 partialTick 插值：位置精确且渲染丝滑，无缓动滞后。
        h.track(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    override fun onDie() {
        handle?.remove()
        handle = null
    }

    /** 惰性生成：在首个 onTick（此时 Move 已更新 status.pos）生成，避免读到未初始化的原点。 */
    private fun ensureSpawned() {
        if (handle != null) return
        val manager = wand.status.particleManager ?: return
        val pos = status.pos
        handle = manager.create()
            .position(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            .color(color)
            .scale(scale)
            .lifetime(-1) // 永存，由 onDie 显式移除
            .spawn()
    }
}