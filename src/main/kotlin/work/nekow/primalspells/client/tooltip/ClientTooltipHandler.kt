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
import work.nekow.primalspells.magic.core.MagicManager
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
                event.tooltipElements.add(Either.right(PouchTooltip(spells, pouchId)))
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
        } else {
            event.tooltipElements.add(Either.left(
                Component.translatable("tooltip.primalspells.hold_alt").withStyle(ChatFormatting.DARK_GRAY)
            ))
        }
        event.tooltipElements.add(Either.right(WandSpellTooltip(spells, wandId)))
        addSelectedSpellLines(event, spells, wandId, pouch = false)
    }

    /**
     * Alt 面板的属性行。
     *
     * **施放延迟 / 充能时间显示的是这根杖的配置值（秒）**，不是当前剩余冷却 ——
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
                sec(s.wandDelaySeconds)).withStyle(ChatFormatting.GOLD)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.recharge",
                sec(s.wandRechargeSeconds)).withStyle(ChatFormatting.DARK_BLUE)
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

    /** 秒显示：两位小数（`0.34`）；整秒只留一位（`1.0`）。 */
    private fun sec(value: Double): String =
        if (value % 1.0 == 0.0) "%.1f".format(value) else "%.2f".format(value)

    private fun addSpellLore(event: RenderTooltipEvent.GatherComponents) {
        val id = ModItems.spellIdOf(event.itemStack.item) ?: return
        val magic = MagicManager.create(id) ?: return
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
        val magic = MagicManager.create(spellId) ?: return
        val item = ModItems.spellItem(spellId)
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
