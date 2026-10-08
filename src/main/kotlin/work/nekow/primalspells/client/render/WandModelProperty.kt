package work.nekow.primalspells.client.render

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.registry.ModItems
import kotlin.math.ceil

/** 原生物品模型的部件帧选择，握柄与主宝石锚点不移动。 */
data class WandModelProperty(val crystal: Boolean = false) : RangeSelectItemModelProperty {
    override fun get(stack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float {
        if (level == null || owner == null) return 0f
        val now = level.gameTime.toDouble() + Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        if (!crystal) return ((now % 48.0) * 32 / 48).toInt().toFloat()
        val id = stack.get(ModItems.WAND_ID)?.wandId ?: return 0f
        val age = now - (flashes[id] ?: return 0f)
        return ceil(3 * (1 - age / 6)).coerceIn(0.0, 3.0).toFloat()
    }

    override fun type(): MapCodec<WandModelProperty> = CODEC

    companion object {
        val CODEC: MapCodec<WandModelProperty> = Codec.BOOL.optionalFieldOf("crystal", false).xmap(::WandModelProperty, WandModelProperty::crystal)
        private val flashes = hashMapOf<String, Long>()
        fun flash(id: String) {
            val now = Minecraft.getInstance().level?.gameTime ?: return
            flashes.entries.removeIf { now - it.value > 20 }
            flashes[id] = now
        }
        fun clear() = flashes.clear()
    }
}
