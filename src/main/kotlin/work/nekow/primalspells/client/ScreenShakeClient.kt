package work.nekow.primalspells.client

import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.network.ScreenShakePayload
import kotlin.math.tanh

/** 有限镜头冲击；每个重音从零起落，重叠不重置已有相位，不改变翻滚与FOV。 */
object ScreenShakeClient {
    private data class Pulse(val start: Double, val duration: Int, val amplitude: Float)
    private val pulses = ArrayList<Pulse>()
    private var world: Any? = null

    /** 客户端入口安装事件，跨世界及登出清除旧冲击。 */
    fun install() {
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> {
            val level = Minecraft.getInstance().level
            if (world !== level) { clear(); world = level }
            val now = level?.gameTime?.toDouble() ?: 0.0
            pulses.removeAll { now - it.start > it.duration + 1 }
        }
        NeoForge.EVENT_BUS.addListener<ViewportEvent.ComputeCameraAngles> { event ->
            val level = Minecraft.getInstance().level ?: return@addListener
            val scale = Minecraft.getInstance().options.damageTiltStrength().get().coerceIn(0.0, 1.0)
            val now = level.gameTime + event.partialTick
            var yaw = 0.0
            var pitch = 0.0
            for (pulse in pulses) {
                val sample = CameraImpulse.sample(now - pulse.start, pulse.duration.toDouble())
                yaw += sample.first * pulse.amplitude
                pitch += sample.second * pulse.amplitude
            }
            // 平滑饱和而非累计重锤；最大偏转低于一度，准星周围保持稳定。
            event.yaw += (tanh(yaw) * 0.45 * scale).toFloat()
            event.pitch += (tanh(pitch) * 0.65 * scale).toFloat()
        }
    }

    /** 收到有限冲击，不重置其他冲击的时钟；同刻只保留较强值。 */
    fun onShake(payload: ScreenShakePayload) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        if (world !== level) { clear(); world = level }
        if (!payload.amplitude.isFinite() || payload.amplitude <= 0f || payload.ticks <= 0) return
        val now = level.gameTime + mc.deltaTracker.getGameTimeDeltaPartialTick(false).toDouble()
        val pulse = Pulse(now, payload.ticks.coerceIn(4, 16), payload.amplitude.coerceIn(0f, 1f))
        val same = pulses.indexOfLast { kotlin.math.abs(it.start - now) < 0.01 }
        if (same >= 0) {
            if (pulses[same].amplitude < pulse.amplitude) pulses[same] = pulse
        } else pulses += pulse
    }

    /** 登出/世界切换清理，避免把上一世界的重音带入新世界。 */
    fun clear() { pulses.clear(); world = null }
}
