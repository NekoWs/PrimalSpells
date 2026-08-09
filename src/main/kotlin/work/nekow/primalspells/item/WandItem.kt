package work.nekow.primalspells.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.PrimalSpells.Companion.debug
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager

class WandItem(properties: Properties) : Item(properties) {

    override fun inventoryTick(stack: ItemStack, level: ServerLevel, owner: Entity, slot: EquipmentSlot?) {
        if (!stack.has(ModItems.WAND_ID)) WandManager.createWand(stack)
        syncSpells(stack)
    }

    fun spell(caster: Entity, stack: ItemStack) {
        val wand = getWand(stack) ?: return
        wand.spell(caster)
        val msg = wand.status.failedReason
        if (msg.isNotEmpty()) {
            msg.forEach { caster.debug(it) }
        }
        caster.debug("Hand: ${wand.hand.joinToString { it::class.simpleName.toString() }}")
        caster.debug("Discard: ${wand.discardPile.joinToString { it::class.simpleName.toString() }}")
        caster.debug("Draw: ${wand.drawPile.joinToString { it::class.simpleName.toString() }}")
    }

    override fun overrideOtherStackedOnMe(
        self: ItemStack, other: ItemStack, slot: Slot,
        action: ClickAction, player: Player, carried: SlotAccess
    ): Boolean {
        if (action != ClickAction.SECONDARY) return false
        val wand = getWand(self) ?: return false
        if (!other.isEmpty) return addToCursor(self, wand, other.item, carried)
        removeToCursor(self, wand, carried)
        return true
    }

    override fun overrideStackedOnOther(
        self: ItemStack, slot: Slot, action: ClickAction, player: Player
    ): Boolean {
        val wand = getWand(self) ?: return false
        if (action == ClickAction.PRIMARY && !slot.item.isEmpty)
            return addFromSlot(self, wand, slot, player)
        if (action == ClickAction.SECONDARY)
            return removeToSlot(self, wand, slot)
        return false
    }

    /** 将光标中的法术添加到法杖当前选中格，若已占则交换回光标。 */
    private fun addToCursor(
        stack: ItemStack, wand: Wand, carriedItem: Item, carried: SlotAccess
    ): Boolean {
        val entry = findMagic(carriedItem) ?: return false
        val target = selectedSlot(stack, wand)
        val existing = placeMagic(wand, stack, target, entry.key)
        if (existing != null) carried.set(makeStack(existing)) else carried.set(ItemStack.EMPTY)
        return true
    }

    /** 从法杖当前选中格取出法术到光标，若选中格为空则阻止拿起法杖。 */
    private fun removeToCursor(stack: ItemStack, wand: Wand, carried: SlotAccess) {
        val target = selectedSlot(stack, wand)
        val magic = clearMagic(wand, stack, target) ?: return
        carried.set(makeStack(magic))
        return
    }

    /** 将槽位中的法术添加到法杖当前选中格（法杖在光标时），若已占则交换回槽位。 */
    private fun addFromSlot(
        stack: ItemStack, wand: Wand, slot: Slot, player: Player
    ): Boolean {
        val entry = findMagic(slot.item.item) ?: return false
        val target = selectedSlot(stack, wand)
        val existing = placeMagic(wand, stack, target, entry.key)
        if (existing != null) slot.set(makeStack(existing)) else slot.safeTake(1, 1, player)
        return true
    }

    /** 从法杖当前选中格取出法术放入目标槽位（法杖在光标时）。 */
    private fun removeToSlot(stack: ItemStack, wand: Wand, slot: Slot): Boolean {
        val target = selectedSlot(stack, wand)
        val magic = clearMagic(wand, stack, target) ?: return false
        val result = makeStack(magic)
        if (!slot.hasItem()) { slot.set(result); return true }
        if (!slot.safeInsert(result).isEmpty) return true
        return false
    }

    /** 将法术放入法杖指定格（若格在列表范围外则用 null 填充），返回该格原有法术（可为 null）。 */
    private fun placeMagic(wand: Wand, stack: ItemStack, target: Int, id: String): work.nekow.primalspells.magic.Magic? {
        while (wand.magics.size <= target) wand.magics.add(null)
        val existing = wand.magics.getOrNull(target)
        wand.magics[target] = MagicManager.create(id)!!
        wand.load(); syncSpells(stack)
        return existing
    }

    /** 清空法杖指定格，返回被移除的法术，若格为空则返回 null 且不触发同步。 */
    private fun clearMagic(wand: Wand, stack: ItemStack, target: Int): work.nekow.primalspells.magic.Magic? {
        val magic = wand.magics.getOrNull(target) ?: return null
        wand.magics[target] = null
        wand.load(); syncSpells(stack)
        return magic
    }

    /** 从 MOD_MAGICS 中查找与给定物品匹配的条目。 */
    private fun findMagic(item: Item) =
        ModItems.MAGICS.entries.firstOrNull { it.value.get() == item }

    /** 根据法术对象创建对应的 ItemStack。 */
    private fun makeStack(magic: work.nekow.primalspells.magic.Magic): ItemStack {
        val item = ModItems.MAGICS[magic.id]?.get() ?: return ItemStack.EMPTY
        return ItemStack(item)
    }

    /** 从物品栈的 WAND_SELECTED_SLOT 组件读取当前选中格索引，限制在有效范围内。 */
    private fun selectedSlot(stack: ItemStack, wand: Wand): Int {
        val size = maxOf(wand.size, wand.magics.size.coerceAtLeast(1))
        return stack.getOrDefault(ModItems.WAND_SELECTED_SLOT, 0).coerceIn(0, size - 1)
    }

    /** 从物品栈获取关联的法杖对象。 */
    private fun getWand(stack: ItemStack): Wand? {
        val id = stack.get(ModItems.WAND_ID) ?: return null
        return WandManager[id, null]
    }

    /** 将法杖的法术列表同步到物品栈的 WAND_SPELLS 组件上。 */
    private fun syncSpells(stack: ItemStack) {
        val wand = getWand(stack) ?: return
        val size = maxOf(wand.size, wand.magics.size)
        val ids = (0 until size).map { i -> wand.magics.getOrNull(i)?.id ?: "" }
        stack.set(ModItems.WAND_SPELLS, ids)
    }
}
