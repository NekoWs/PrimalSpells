package work.nekow.primalspells.client

import com.mojang.datafixers.util.Either
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.WandItem

object ClientTooltipHandler {
    @SubscribeEvent
    fun onGatherTooltip(event: RenderTooltipEvent.GatherComponents) {
        val stack = event.itemStack
        if (stack.item !is WandItem) return
        if (!Minecraft.getInstance().hasShiftDown()) return
        val wandId = stack.get(ModItems.WAND_ID.get())
        val display = wandId?.wandId
            ?: Component.translatable("tooltip.primalspells.wand_id.ungenerated").string
        event.tooltipElements.add(
            Either.left(
                Component.translatable("tooltip.primalspells.wand_id", display).withStyle(ChatFormatting.GRAY)
            )
        )
    }
}
