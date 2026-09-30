package work.nekow.primalspells.client

import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.network.ScreenShakePayload
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos

/**
 * 镜头冲击（屏幕震动 + FOV 抽动）的**客户端求值**。
 *
 * 服务端只在重音那一拍发一条 8 字节的 [ScreenShakePayload]（见 `util/ScreenShake.kt`），
 * 之后每一帧的偏移都由这里在本地算 —— **零逐 tick 带宽**，与粒子那套「客户端本地求值」的思路一致。
 *
 * ## 手感是怎么配出来的
 *
 * 1. **衰减要快**：振幅按 `(剩余/总时长)^1.35` 掉，一记重音应该在 0.3 秒内明显收住，
 *    拖长的震动只会变成「晕」而不是「重」；
 * 2. **前 4 tick 叠一记方向性冲击**（[KICK_TICKS]）：镜头先被往下压一下再抖 —— 这是「被撞了一下」
 *    与「画面在飘」的分界；
 * 3. **抖动不是随机数**：三组不同频率的正弦叠加（偏航/俯仰/翻滚各用不同频率），
 *    两端算得一样、可复现、也不会出现随机跳变；相位按 tick 推进、渲染帧用 partialTick 插值，
 *    所以 20fps 与 260fps 看到的抖动频率一致；
 * 4. **FOV 抽一下**（[MAX_FOV_PUNCH]）：冲击瞬间视野微微张开，像被推开一点；
 * 5. **玩家说了算**：整体乘原版「受伤视角晃动」强度（`options.damageTiltStrength`）——
 *    设成 0 的玩家（无障碍设置里很常见）完全看不到震动，我们只在设置允许时才动手。
 */
object ScreenShakeClient {

    /** 满强度时的角度上限（度）。偏航给得最多，因为它最容易被眼睛读到。 */
    private const val MAX_YAW_DEG = 2.6f
    private const val MAX_PITCH_DEG = 1.8f
    private const val MAX_ROLL_DEG = 1.4f

    /** 满强度时 FOV 的瞬时张开量（度） */
    private const val MAX_FOV_PUNCH = 3.5f

    /** 相位推进速度（弧度/tick）：约 1.3 Hz 的抖动 */
    private const val PHASE_PER_TICK = 0.42f

    /** 起始方向性冲击持续的 tick 数 */
    private const val KICK_TICKS = 4.0f

    /** 方向性冲击的俯仰量（度，正 = 低头）：镜头先被压一下 */
    private const val KICK_PITCH_DEG = 2.2f

    private var ticksLeft = 0
    private var totalTicks = 0
    private var amplitude = 0f
    private var phase = 0f

    /** 由客户端入口安装：注册事件、接管每 tick 的衰减。 */
    fun install() {
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> { tick() }
        NeoForge.EVENT_BUS.addListener<ViewportEvent.ComputeCameraAngles> { event -> applyAngles(event) }
        NeoForge.EVENT_BUS.addListener<ViewportEvent.ComputeFov> { event -> applyFov(event) }
    }

    /** 收到一条冲击指令：**取更强的那一条**（同刻多条重音不会叠加成晕眩）。 */
    fun onShake(payload: ScreenShakePayload) {
        val stronger = payload.amplitude >= amplitude || ticksLeft <= 0
        if (stronger) {
            amplitude = payload.amplitude.coerceIn(0f, 1f)
            totalTicks = payload.ticks.coerceAtLeast(1)
            phase = 0f
        }
        ticksLeft = max(ticksLeft, payload.ticks)
    }

    private fun tick() {
        if (ticksLeft <= 0) {
            amplitude = 0f
            return
        }
        ticksLeft--
        phase += PHASE_PER_TICK
    }

    /** 玩家可关闭：`0` = 完全不晃（原版无障碍选项「受伤视角晃动」）。 */
    private fun userScale(): Float =
        Minecraft.getInstance().options.damageTiltStrength().get().toFloat().coerceIn(0f, 1f)

    /**
     * 当前这一帧的振幅系数：`强度 × 时间衰减 × 玩家设置`。
     *
     * @param partialTick 渲染帧内的插值量，用来把「相位」平滑到帧级（帧率无关）
     */
    private fun frameAmount(partialTick: Double): Float {
        if (ticksLeft <= 0 || amplitude <= 0f) return 0f
        val scale = userScale()
        if (scale <= 0f) return 0f
        val t = (ticksLeft.toFloat() / totalTicks.toFloat()).coerceIn(0f, 1f)
        return amplitude * t.pow(1.35f) * scale
    }

    /** 起始那一下方向性冲击的强度（1 → 0）。 */
    private fun kickAmount(): Float {
        if (ticksLeft <= 0 || totalTicks <= 1) return 0f
        val elapsed = (totalTicks - ticksLeft).toFloat()
        return (1f - elapsed / KICK_TICKS).coerceIn(0f, 1f) * amplitude * userScale()
    }

    private fun applyAngles(event: ViewportEvent.ComputeCameraAngles) {
        val amount = frameAmount(event.partialTick)
        val kick = kickAmount()
        if (amount <= 0f && kick <= 0f) return
        val p = (phase + event.partialTick.toFloat() * PHASE_PER_TICK)
        // 三组不同频率的正弦叠加：低频给「晃」，高频给「颤」
        val yaw = sin(p) * 0.72f + sin(p * 2.7f) * 0.28f
        val pitch = cos(p * 1.3f) * 0.68f + sin(p * 3.1f) * 0.32f
        val roll = sin(p * 0.9f) * 0.85f + cos(p * 2.3f) * 0.15f
        event.yaw += yaw * MAX_YAW_DEG * amount
        event.pitch += pitch * MAX_PITCH_DEG * amount + kick * KICK_PITCH_DEG
        event.roll += roll * MAX_ROLL_DEG * amount
    }

    private fun applyFov(event: ViewportEvent.ComputeFov) {
        val kick = kickAmount()
        if (kick <= 0f) return
        // 冲击瞬间视野被「推开」一点；KICK_PITCH_DEG 是低头，这里是张开
        event.fov = event.fov + kick * MAX_FOV_PUNCH
    }
}
