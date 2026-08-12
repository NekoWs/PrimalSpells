package work.nekow.primalspells.event

import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import work.nekow.primalspells.item.SpellPouchInventory
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.SpellPouchMenu

/**
 * 法术小包右键处理（服务端）：
 * 手持小包右键 → 打开容器 UI（extraData 携带小包容器槽位索引供客户端重建）。
 */
object PouchEventHandler {

    @SubscribeEvent
    fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity as? Player ?: return
        if (player.level().isClientSide) return
        val stack = event.itemStack
        if (stack.item !is SpellPouchItem) return

        // 服务端生成小包唯一 id（供浮窗等按 id 定位，组件随物品同步）
        SpellPouchItem.ensurePouchId(stack)

        // 定位小包在玩家容器中的槽位索引
        val menu = player.containerMenu
        val pouchSlot = (0 until menu.slots.size).firstOrNull { i ->
            val s = menu.getSlot(i).item
            !s.isEmpty && s.item is SpellPouchItem && ItemStack.isSameItemSameComponents(s, stack)
        } ?: return

        event.isCanceled = true
        player.openMenu(object : MenuProvider {
            override fun getDisplayName() = stack.hoverName
            override fun createMenu(containerId: Int, inv: Inventory, p: Player): SpellPouchMenu =
                SpellPouchMenu(containerId, inv, SpellPouchInventory(stack))
        }) { buf -> buf.writeVarInt(pouchSlot) }
    }
}
