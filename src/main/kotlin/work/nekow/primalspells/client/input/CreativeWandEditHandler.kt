package work.nekow.primalspells.client.input

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.network.EditWandPayload
import work.nekow.primalspells.registry.ModItems

/** 创造物品栏本地管理光标；仅把法杖原右键装卸转交服务端，不创建额外窗口。 */
object CreativeWandEditHandler {
    /** 仅处理创造菜单真实法杖槽上的普通右键；其他输入继续交给原版。 */
    @SubscribeEvent
    fun click(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (event.button != 1) return
        if (Minecraft.getInstance().hasShiftDown()) return
        val screen = event.screen as? CreativeModeInventoryScreen ?: return
        val player = Minecraft.getInstance().player?.takeIf { it.isCreative } ?: return
        val target = screen.menu.slots.firstOrNull { it.isActive &&
            event.mouseX >= screen.getLeftPos() + it.x && event.mouseX < screen.getLeftPos() + it.x + 18 &&
            event.mouseY >= screen.getTopPos() + it.y && event.mouseY < screen.getTopPos() + it.y + 18 } ?: return
        val stack = target.item.takeIf { it.item is WandItem } ?: return
        val id = stack.get(ModItems.WAND_ID)?.wandId ?: return
        val menu = player.inventoryMenu
        val slot = menu.slots.firstOrNull { it.container === target.container && it.slotIndex == target.slotIndex } ?: return
        val cursor = screen.menu.carried
        val spell = if (cursor.isEmpty) "" else ModItems.spellIdOf(cursor.item) ?: return
        ClientPacketDistributor.sendToServer(EditWandPayload(menu.containerId, slot.index, id,
            stack.getOrDefault(ModItems.WAND_SELECTED_SLOT, 0), spell, cursor.count,
            cursor.get(ModItems.SPELL_USES) ?: -1))
        event.isCanceled = true
    }
}
