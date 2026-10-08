package work.nekow.primalspells.client.render

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.registry.ModItems

/** 原生物品模型的部件帧选择，握柄与主宝石锚点不移动。 */
data class WandModelProperty(val crystal: Boolean = false) : RangeSelectItemModelProperty {
    override fun get(stack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float {
        if (level == null || owner == null) return 0f
        val now = level.gameTime.toDouble() + Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        if (!crystal) return ((now % 48.0) * 32 / 48).toInt().toFloat()
        val id = stack.get(ModItems.WAND_ID)?.wandId ?: return 0f
        return (15 * brightness(flashes[id] ?: return 0f, now)).toFloat()
    }

    override fun type(): MapCodec<WandModelProperty> = CODEC

    companion object {
        val CODEC: MapCodec<WandModelProperty> = Codec.BOOL.optionalFieldOf("crystal", false).xmap(::WandModelProperty, WandModelProperty::crystal)
        private data class Flash(val start: Double, val initial: Double)
        private val flashes = hashMapOf<String, Flash>()
        private fun brightness(flash: Flash, now: Double): Double {
            val age = (now - flash.start).coerceAtLeast(0.0)
            fun smooth(x: Double): Double = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
            return (flash.initial + (1 - flash.initial) * smooth(age)) * (1 - smooth((age - 1) / 6))
        }
        /** 成功施法后继承当前宝石亮度，客户端逐帧计算短余光。 */
        fun flash(id: String) {
            val mc = Minecraft.getInstance()
            val now = (mc.level?.gameTime ?: return) + mc.deltaTracker.getGameTimeDeltaPartialTick(false).toDouble()
            flashes.entries.removeIf { now - it.value.start > 20 }
            val initial = flashes[id]?.let { brightness(it, now) } ?: 0.0
            flashes[id] = Flash(now, initial)
        }
        /** 登出时清除按法杖ID缓存的余光。 */
        fun clear() = flashes.clear()
    }
}
