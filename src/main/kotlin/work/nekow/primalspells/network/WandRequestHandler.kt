package work.nekow.primalspells.network

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.registry.ModItems

/** 服务端编杖与预览请求；身份、菜单、范围与创造权限校验都在修改状态之前完成。 */
internal object WandRequestHandler {
    fun preview(player: ServerPlayer, payload: WandPreviewPayload) {
        val now = player.level().gameTime
        val data = player.persistentData
        val last = data.getLongOr("primalspells:preview_at", -100L)
        if (payload.preview != null || now - last < 5) return
        data.putLong("primalspells:preview_at", now)
        val stacks = player.containerMenu.slots.map { it.item } + player.containerMenu.carried
        val stack = stacks.firstOrNull { it.item is WandItem && it.get(ModItems.WAND_ID)?.wandId == payload.wandId }
        val wand = stack?.let(WandItem::getWand) ?: return
        PacketDistributor.sendToPlayer(player, WandPreviewPayload(payload.wandId, wand.preview(player)))
    }

    fun edit(player: ServerPlayer, payload: EditWandPayload) {
        if (!player.isCreative || payload.creativeCount < 0) return
        val menu = player.containerMenu
        if (menu.containerId != payload.menuId) return
        val slot = menu.slots.getOrNull(payload.slot) ?: return
        val stack = slot.item
        val item = stack.item as? WandItem ?: return
        if (!slot.mayPickup(player) || stack.get(ModItems.WAND_ID)?.wandId != payload.wandId) return
        val wand = WandItem.getWand(stack) ?: return
        if (payload.spellSlot !in 0 until maxOf(wand.size, wand.magics.size)) return
        // 只允许已注册散卡与合法堆叠数，不接收客户端任意组件或法杖数值。
        val cursor = if (payload.creativeCount == 0 && payload.creativeSpell.isEmpty()) ItemStack.EMPTY else {
            val spellItem = ModItems.spellItem(payload.creativeSpell) ?: return
            ItemStack(spellItem).also {
                if (payload.creativeCount !in 1..it.maxStackSize) return
                it.count = payload.creativeCount
            }
        }
        menu.carried = cursor
        stack.set(ModItems.WAND_SELECTED_SLOT, payload.spellSlot)
        item.overrideOtherStackedOnMe(stack, cursor, slot, ClickAction.SECONDARY, player, object : SlotAccess {
            override fun get() = menu.carried
            override fun set(value: ItemStack): Boolean { menu.carried = value; return true }
        })
        // 创造界面忽略原版单独光标包，完整同步包含服务端权威光标。
        menu.broadcastFullState()
        PacketDistributor.sendToPlayer(player, WandPreviewPayload(payload.wandId, wand.preview(player)))
    }

    fun select(player: ServerPlayer, payload: SelectSpellSlotPayload) {
        fun matches(stack: ItemStack) = if (payload.pouch) {
            stack.item is SpellPouchItem && SpellPouchItem.getPouchId(stack) == payload.targetId
        } else {
            stack.item is WandItem && stack.get(ModItems.WAND_ID)?.wandId == payload.targetId
        }
        // 法杖也可在当前箱子内；只读取可拾取槽位或本人背包中的真实物品。
        val stack = player.containerMenu.slots.firstOrNull { it.mayPickup(player) && matches(it.item) }?.item
            ?: (0 until player.inventory.containerSize).firstNotNullOfOrNull { player.inventory.getItem(it).takeIf(::matches) }
            ?: return
        val size = if (payload.pouch) SpellPouchItem.SLOT_COUNT else WandItem.getWandData(stack)?.second?.size ?: return
        if (payload.slot !in 0 until size) return
        stack.set(if (payload.pouch) ModItems.POUCH_SELECTED_SLOT.get() else ModItems.WAND_SELECTED_SLOT.get(), payload.slot)
        player.containerMenu.broadcastChanges()
    }
}
