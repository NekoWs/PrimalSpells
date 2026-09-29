package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.pdParticles

/**
 * 投射物本体粒子：表示投射物当前所在位置的一个粒子，每 tick 跟随 `status.pos` 移动。
 *
 * 与 [Trajectory]（尾迹）不同，本体粒子只保留一个（永存，死亡时移除），
 * 始终贴在投射物当前位置，作为「投射物现在在哪」的视觉标记。
 *
 * 阶段：[EffectPhase.VISUAL] —— 在位置积分与轨道修正之后执行，读到的是本 tick 的最终位置。
 *
 * @param color 本体粒子颜色
 * @param scale 本体粒子缩放
 */
class ProjectileBody(
    val color: Color,
    val scale: Float = 1f,
) : BaseEffect() {
    override val phase = EffectPhase.VISUAL

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

    /** 惰性生成：在首个 onTick（此时位置积分已完成）生成，避免读到未初始化的原点。 */
    private fun ensureSpawned() {
        if (handle != null) return
        val level = caster.level() as? ServerLevel ?: return
        val manager = wand.pdParticles(level)
        val pos = status.pos
        handle = manager.create()
            .position(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            .color(color)
            .scale(scale)
            .lifetime(-1) // 永存，由 onDie 显式移除
            .spawn()
    }
}