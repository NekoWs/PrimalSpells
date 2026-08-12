package work.nekow.primalspells.client

import com.mojang.datafixers.util.Either
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.utils.Lore

object ClientTooltipHandler {

    @SubscribeEvent
    fun onGatherTooltip(event: RenderTooltipEvent.GatherComponents) {
        // 法术小包：附加上容器内法术的收纳袋式网格
        if (event.itemStack.item is SpellPouchItem) {
            val spells = SpellPouchItem.getSpells(event.itemStack)
            if (spells.isNotEmpty()) {
                event.tooltipElements.add(Either.right(PouchTooltip(spells)))
            }
            return
        }
        val (wandId, spells) = WandEventHandler.getWandData(event.itemStack) ?: run {
            addSpellLore(event)
            return
        }
        if (Minecraft.getInstance().hasAltDown()) {
            addStatLines(event, wandId)
        } else {
            event.tooltipElements.add(Either.left(
                Component.translatable("tooltip.primalspells.hold_alt").withStyle(ChatFormatting.DARK_GRAY)
            ))
        }
        event.tooltipElements.add(Either.right(WandSpellTooltip(spells, wandId)))
        addSelectedSpellLines(event, spells, wandId)
    }

    private fun addStatLines(event: RenderTooltipEvent.GatherComponents, wandId: String) {
        val s = WandHudRenderer.getStats(wandId) ?: return
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.mana",
                "%.1f".format(s.currentMana), "%.1f".format(s.maxMana)).withStyle(ChatFormatting.BLUE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.delay",
                s.currentDelay).withStyle(ChatFormatting.GOLD)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.recharge",
                s.currentRecharge).withStyle(ChatFormatting.DARK_BLUE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.cast", s.cast).withStyle(ChatFormatting.WHITE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.charge",
                "%.1f".format(s.charge)).withStyle(ChatFormatting.BLUE)
        ))
    }

    private fun addSpellLore(event: RenderTooltipEvent.GatherComponents) {
        val id = ModItems.MAGICS.entries.firstOrNull { it.value.get() == event.itemStack.item }?.key ?: return
        val magic = MagicManager.create(id) ?: return
        for ((type, args) in magic.lore) {
            addLoreLine(event, type, args, id)
        }
    }

    private fun addSelectedSpellLines(event: RenderTooltipEvent.GatherComponents, spells: List<String>, wandId: String) {
        val componentSlot = event.itemStack.get(ModItems.WAND_SELECTED_SLOT.get())
        if (componentSlot != null) {
            WandTooltipTracker.setSelectedSlot(wandId, componentSlot)
        }
        val selected = WandTooltipTracker.getSelectedSlot(wandId)
        val spellId = spells.getOrNull(selected) ?: return
        if (spellId.isEmpty()) return
        val magic = MagicManager.create(spellId) ?: return
        val item = ModItems.MAGICS[spellId]?.get()
        if (item != null) {
            event.tooltipElements.add(Either.left(
                Component.translatable(item.descriptionId).withStyle(ChatFormatting.WHITE)
            ))
        }
        for ((type, args) in magic.lore) {
            addLoreLine(event, type, args, spellId)
        }
    }

    private fun addLoreLine(event: RenderTooltipEvent.GatherComponents, type: Lore, args: Array<out Any>, spellId: String) {
        val comp = when (type) {
            Lore.BR -> Component.literal("").withStyle(ChatFormatting.DARK_GRAY)
            Lore.DESCRIPTION -> Component.translatable("lore.primalspells.$spellId.desc").withStyle(ChatFormatting.GRAY)
            else -> Component.translatable(type.key, *args).withStyle(ChatFormatting.GRAY)
        }
        event.tooltipElements.add(Either.left(comp))
    }
}
