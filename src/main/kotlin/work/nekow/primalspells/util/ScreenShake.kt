package work.nekow.primalspells.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import work.nekow.primalspells.network.ScreenShakePayload

/** 近场爆发的服务端镜头提示；距离边缘归零，旁观者不受影响，玩家设置由客户端尊重。 */
object ScreenShake {

    fun shake(level: ServerLevel, center: Vec3, radius: Double, amplitude: Float, ticks: Int) {
        if (!amplitude.isFinite() || !radius.isFinite() || radius <= 0.0 || amplitude <= 0f || ticks <= 0) return
        val players = level.players()
        if (players.isEmpty()) return
        val payload = ScreenShakePayload(amplitude.coerceIn(0f, 1f), ticks)
        val radiusSq = radius * radius
        for (player in players) {
            // 近处才有反馈；连续平方衰减在边缘归零。
            val distSq = player.position().distanceToSqr(center)
            if (player.isSpectator || distSq >= radiusSq) continue
            val falloff = (1.0 - distSq / radiusSq).let { (it * it).toFloat() }
            PacketDistributor.sendToPlayer(
                player,
                ScreenShakePayload(payload.amplitude * falloff, payload.ticks),
            )
        }
    }
}
