package work.nekow.primalspells.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import work.nekow.primalspells.network.ScreenShakePayload

/**
 * 镜头冲击的**服务端入口**：给范围内每个玩家发一条 [ScreenShakePayload]。
 *
 * 用法只关心「在哪里、多轻、多久」：
 *
 * ```
 * // 黑洞：站在范围里就一直极轻微地抖（每 tick 续期一次，离开范围自然停）
 * ScreenShake.shake(level, player.position(), radius = 1.0, amplitude = 0.05f, ticks = 3)
 * // 黑洞：开场那一下
 * ScreenShake.shake(level, center, radius = 24.0, amplitude = 0.45f, ticks = 8)
 * ```
 *
 * ⚠️ **第 20 轮实机反馈后收敛了用法**：只保留「**持续性**的、很轻的」抖动（黑洞范围内），
 * 以及黑洞开场那一下；护盾与重力场**完全不再用**镜头冲击 —— 一记记重锤式震动看久了会烦，
 * 力量感应该来自特效本身的节奏。调用点见 [BlackHole]。
 * 「怎么晃、晃不晃」全在客户端（`client/ScreenShakeClient`），并受原版「受伤视角晃动」强度控制
 * —— 关掉它的玩家不会被打扰。
 *
 * 注意这是**纯表现**：不参与任何结算，也不改变粒子状态；漏发/晚发只影响手感，不影响正确性。
 *
 * @param radius    生效半径（方块）：这是「看得见这个特效」的范围，比效果本身的半径大一圈
 * @param amplitude 强度 0..1；0 或负数直接跳过（不发包）
 * @param ticks     持续 tick 数；0 或负数直接跳过
 */
object ScreenShake {

    fun shake(level: ServerLevel, center: Vec3, radius: Double, amplitude: Float, ticks: Int) {
        if (amplitude <= 0f || ticks <= 0) return
        val players = level.players()
        if (players.isEmpty()) return
        val payload = ScreenShakePayload(amplitude.coerceIn(0f, 1f), ticks)
        val radiusSq = radius * radius
        for (player in players) {
            // 距离越远越轻：边缘处只剩 35%，避免站在远处也被同等强度晃
            val distSq = player.position().distanceToSqr(center)
            if (distSq > radiusSq) continue
            val falloff = (1.0 - 0.65 * (distSq / radiusSq)).toFloat()
            PacketDistributor.sendToPlayer(
                player,
                ScreenShakePayload(payload.amplitude * falloff, payload.ticks),
            )
        }
    }
}
