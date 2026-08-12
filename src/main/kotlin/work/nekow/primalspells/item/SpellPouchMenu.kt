package work.nekow.primalspells.item

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * 法术小包容器菜单：16 个法术槽（2×8）+ 玩家背包。
 */
class SpellPouchMenu(
    containerId: Int,
    private val playerInventory: Inventory,
    val pouchInventory: SpellPouchInventory,
) : AbstractContainerMenu(ModMenus.SPELL_POUCH.get(), containerId) {

    init {
        for (i in 0 until SpellPouchInventory.SLOT_COUNT) {
            addSlot(Slot(pouchInventory, i, 8 + (i % 8) * 18, 18 + (i / 8) * 18))
        }
        addStandardInventorySlots(playerInventory, 8, 84)
    }

    override fun stillValid(player: Player): Boolean = true

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var itemstack = ItemStack.EMPTY
        val slot = slots[index]
        if (slot.hasItem()) {
            val stack1 = slot.item
            itemstack = stack1.copy()
            if (index < SpellPouchInventory.SLOT_COUNT) {
                if (!moveItemStackTo(stack1, SpellPouchInventory.SLOT_COUNT, slots.size, true)) {
                    return ItemStack.EMPTY
                }
            } else {
                if (!moveItemStackTo(stack1, 0, SpellPouchInventory.SLOT_COUNT, false)) {
                    return ItemStack.EMPTY
                }
            }
            if (stack1.isEmpty) {
                slot.set(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }
        }
        return itemstack
    }
}
