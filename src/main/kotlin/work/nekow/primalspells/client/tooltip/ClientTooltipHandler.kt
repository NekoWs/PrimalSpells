package work.nekow.primalspells.client.tooltip

import com.mojang.datafixers.util.Either
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import work.nekow.primalspells.client.hud.WandHudRenderer
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.util.Lore

/**
 * 组装物品 tooltip：法杖属性/槽位网格/选中法术，法术小包槽位网格/选中法术，单个法术的描述行。
 *
 * 法杖与小包共用同一条「选中槽」逻辑（见 [addSelectedSpellLines]）：先按物品上的选中槽组件
 * 校正本地缓存 [SpellSlotTracker]，再展示该槽法术的名称与词条。
 */
object ClientTooltipHandler {

    @SubscribeEvent
    fun onGatherTooltip(event: RenderTooltipEvent.GatherComponents) {
        val stack = event.itemStack

        // 法术小包：槽位网格 + 选中槽法术
        if (stack.item is SpellPouchItem) {
            val spells = SpellPouchItem.getSpells(stack)
            if (spells.isNotEmpty()) {
                val pouchId = SpellPouchItem.getPouchId(stack).orEmpty()
                event.tooltipElements.add(Either.right(PouchTooltip(spells, pouchId, SpellPouchItem.getUses(stack))))
                addSelectedSpellLines(event, spells, pouchId, pouch = true)
            }
            return
        }

        // 法杖：Alt 看属性，否则只提示；随后是槽位网格 + 选中槽法术
        val (wandId, spells) = WandItem.getWandData(stack) ?: run {
            addSpellLore(event)
            return
        }
        if (Minecraft.getInstance().hasAltDown()) {
            addStatLines(event, wandId)
            event.tooltipElements.add(Either.left(Component.translatable("tooltip.primalspells.stabilizing_seal").withStyle(ChatFormatting.WHITE)))
            event.tooltipElements.add(Either.left(Component.translatable("tooltip.primalspells.charging_crystal").withStyle(ChatFormatting.BLUE)))
        } else {
            event.tooltipElements.add(Either.left(
                Component.translatable("tooltip.primalspells.hold_alt").withStyle(ChatFormatting.DARK_GRAY)
            ))
        }
        event.tooltipElements.add(Either.left(Component.translatable("preview.primalspells.hint").withStyle(ChatFormatting.DARK_GRAY)))
        event.tooltipElements.add(Either.right(WandSpellTooltip(spells, wandId,
            uses = stack.get(ModItems.WAND_SPELL_USES) ?: emptyList())))
        addSelectedSpellLines(event, spells, wandId, pouch = false)
    }

    /**
     * Alt 面板的属性行。
     *
     * **施放延迟 / 充能时间显示的是这根杖的配置值（刻）**，不是当前剩余冷却 ——
     * 剩余冷却由 HUD 的进度条表达；面板要回答的是"这根杖多快"，第 27 轮之前这里显示的是实时计数器
     * （一旦计数器改成小数 tick，还会打出 `3.7999999` 这种数）。
     */
    private fun addStatLines(event: RenderTooltipEvent.GatherComponents, wandId: String) {
        val s = WandHudRenderer.getStats(wandId) ?: return
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.mana",
                "%.1f".format(s.currentMana), "%.1f".format(s.maxMana)).withStyle(ChatFormatting.BLUE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.delay",
                ticks(s.wandDelayTicks)).withStyle(ChatFormatting.GOLD)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.recharge",
                ticks(s.wandRechargeTicks)).withStyle(ChatFormatting.DARK_BLUE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.cast", s.cast).withStyle(ChatFormatting.WHITE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.charge",
                "%.1f".format(s.charge)).withStyle(ChatFormatting.BLUE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.size", s.size).withStyle(ChatFormatting.GRAY)
        ))
    }

    private fun ticks(value: Double): String = value.toInt().toString()

    private fun addSpellLore(event: RenderTooltipEvent.GatherComponents) {
        val id = ModItems.spellIdOf(event.itemStack.item) ?: return
        val magic = SpellRegistry.create(id) ?: return
        addUsesLine(event, magic, event.itemStack.get(ModItems.SPELL_USES))
        for ((type, args) in magic.lore) {
            addLoreLine(event, type, args, id)
        }
    }

    /**
     * 展示「当前选中槽」里的法术：名称 + 词条。
     *
     * @param targetId 法杖 id 或小包 id（也是 [SpellSlotTracker] 的键）
     * @param pouch    true = 读小包的选中槽组件，false = 读法杖的
     */
    private fun addSelectedSpellLines(
        event: RenderTooltipEvent.GatherComponents,
        spells: List<String>,
        targetId: String,
        pouch: Boolean,
    ) {
        val componentSlot = event.itemStack.get(
            if (pouch) ModItems.POUCH_SELECTED_SLOT.get() else ModItems.WAND_SELECTED_SLOT.get()
        )
        if (componentSlot != null) {
            SpellSlotTracker.setSelectedSlot(targetId, componentSlot)
        }
        val selected = SpellSlotTracker.getSelectedSlot(targetId)
        val spellId = spells.getOrNull(selected) ?: return
        if (spellId.isEmpty()) return
        val magic = SpellRegistry.create(spellId) ?: return
        val item = ModItems.spellItem(spellId)
        if (item != null) {
            event.tooltipElements.add(Either.left(
                Component.translatable(item.descriptionId).withStyle(ChatFormatting.WHITE)
            ))
        }
        val uses = event.itemStack.get(if (pouch) ModItems.POUCH_SPELL_USES else ModItems.WAND_SPELL_USES)?.getOrNull(selected)
        addUsesLine(event, magic, uses)
        for ((type, args) in magic.lore) {
            addLoreLine(event, type, args, spellId)
        }
    }

    private fun addUsesLine(event: RenderTooltipEvent.GatherComponents, card: work.nekow.primalspells.magic.core.Magic, uses: Int?) {
        if (card.maxUses <= 0) return
        val remaining = if (uses != null && uses >= 0) uses.coerceAtMost(card.maxUses) else card.maxUses
        event.tooltipElements.add(Either.left(Component.translatable("tooltip.primalspells.spell_uses", remaining, card.maxUses)
            .withStyle(if (remaining == 0) ChatFormatting.RED else ChatFormatting.GRAY)))
        if (remaining == 0) event.tooltipElements.add(Either.left(Component.translatable("tooltip.primalspells.spell_exhausted")
            .withStyle(ChatFormatting.DARK_GRAY)))
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
