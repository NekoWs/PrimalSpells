package work.nekow.primalspells.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells

/**
 * 服务端 → 客户端：**镜头冲击**（屏幕震动 / FOV 抽动）。
 *
 * 为什么需要它：粒子再多，只要镜头纹丝不动，「力量感」就始终差一口气 ——
 * 黑洞塌缩、重力场砸地、护盾展开与破碎这些**节拍上的重音**，最有效的表现是让画面被"撞"一下。
 * 本模组所有粒子都在服务端生成（见 `docs/architecture.md` 第 9 节），而镜头在客户端，
 * 所以重音必须由服务端向附近玩家发一条极小的包（8 字节），由客户端本地逐帧求值。
 *
 * **玩家自主权**：客户端会按原版的「受伤视角晃动」强度（`options.damageTiltStrength`，
 * 设成 0 即完全关闭）整体缩放；关掉它的玩家不会看到任何震动。
 *
 * @param amplitude 强度 0..1（1 = 最强重音）。由客户端映射为角度：偏航 ±2.6°、俯仰 ±1.8°、翻滚 ±1.4°、FOV 抽动 ±3.5°
 * @param ticks     持续 tick 数（20 tick = 1 秒）；强度按 `(剩余/总时长)^1.35` 衰减，前 4 tick 额外叠一记方向性冲击
 */
data class ScreenShakePayload(
    val amplitude: Float,
    val ticks: Int,
) : CustomPacketPayload {

    companion object {
        val ID = CustomPacketPayload.Type<ScreenShakePayload>(
            Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "screen_shake")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ScreenShakePayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, ScreenShakePayload> {
                override fun decode(buf: RegistryFriendlyByteBuf) =
                    ScreenShakePayload(buf.readFloat(), buf.readVarInt())

                override fun encode(buf: RegistryFriendlyByteBuf, p: ScreenShakePayload) {
                    buf.writeFloat(p.amplitude)
                    buf.writeVarInt(p.ticks)
                }
            }
    }

    override fun type() = ID
}
