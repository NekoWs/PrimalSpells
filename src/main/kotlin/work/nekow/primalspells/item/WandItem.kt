package work.nekow.primalspells.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import work.nekow.primalspells.item.component.WandID
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.network.SyncWandStatsPayload
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager
import java.util.*

class WandItem(properties: Properties) : Item(properties) {

    companion object {
        private val lastCastWand: MutableMap<UUID, WandID> = hashMapOf()

        fun getWand(stack: ItemStack): Wand? {
            val id = stack.get(ModItems.WAND_ID) ?: return null
            return WandManager[id, null]
        }

        /**
         * 通过网络包将法杖属性同步到指定玩家客户端。
         *
         * @param stack  法杖物品栈
         * @param player 目标玩家
         */
        fun syncStats(stack: ItemStack, player: ServerPlayer) {
            val wand = getWand(stack) ?: return
            val wandId = stack.get(ModItems.WAND_ID)?.wandId ?: return
            PacketDistributor.sendToPlayer(player, SyncWandStatsPayload(
                wandId,
                wand.status.mana,
                wand.mana,
                wand.status.delay,
                wand.status.recharge,
                wand.cast,
                wand.status.lastDelay,
                wand.status.lastRecharge,
                wand.charge,
                wand.size
            ))
        }
    }

    override fun inventoryTick(stack: ItemStack, level: ServerLevel, owner: Entity, slot: EquipmentSlot?) {
        if (!stack.has(ModItems.WAND_ID)) WandManager.createWand(stack)
        tickSync(stack)
        // 法杖持于主手/副手时驱动被动法术（Wand.tick 内部按游戏 tick 去重）
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            getWand(stack)?.tick(owner)
        }
        if (owner is ServerPlayer) {
            val wandId = stack.get(ModItems.WAND_ID)
            if (wandId == lastCastWand[owner.uuid]) {
                syncStats(stack, owner)
            }
        }
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (!level.isClientSide) {
            player.startUsingItem(hand)
        }
        return InteractionResult.CONSUME
    }

    override fun getUseDuration(stack: ItemStack, entity: LivingEntity) = 72000

    override fun onUseTick(level: Level, entity: LivingEntity, stack: ItemStack, remainingUseDuration: Int) {
        if (level.isClientSide) return
        if (entity !is Player) return
        stack.get(ModItems.WAND_ID)?.let { lastCastWand[entity.uuid] = it }
        getWand(stack)?.spell(entity)
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

    private fun addToCursor(
        stack: ItemStack, wand: Wand, carriedItem: Item, carried: SlotAccess
    ): Boolean {
        val spellId = findMagic(carriedItem) ?: return false
        val target = selectedSlot(stack, wand)
        val existing = placeMagic(wand, stack, target, spellId)
        if (existing != null) carried.set(makeStack(existing)) else carried.set(ItemStack.EMPTY)
        return true
    }

    private fun removeToCursor(stack: ItemStack, wand: Wand, carried: SlotAccess) {
        val target = selectedSlot(stack, wand)
        val magic = clearMagic(wand, stack, target) ?: return
        carried.set(makeStack(magic))
    }

    private fun placeMagic(wand: Wand, stack: ItemStack, target: Int, id: String): work.nekow.primalspells.magic.Magic? {
        while (wand.magics.size <= target) wand.magics.add(null)
        val existing = wand.magics.getOrNull(target)
        wand.magics[target] = MagicManager.create(id)!!
        wand.load(); tickSync(stack)
        return existing
    }

    private fun clearMagic(wand: Wand, stack: ItemStack, target: Int): work.nekow.primalspells.magic.Magic? {
        val magic = wand.magics.getOrNull(target) ?: return null
        wand.magics[target] = null
        wand.load(); tickSync(stack)
        return magic
    }

    private fun findMagic(item: Item) = ModItems.spellIdOf(item)

    private fun makeStack(magic: work.nekow.primalspells.magic.Magic): ItemStack {
        val item = ModItems.spellItem(magic.id) ?: return ItemStack.EMPTY
        return ItemStack(item)
    }

    /**
     * 从物品组件读取选中槽位索引并钳制在法杖容量范围内。
     *
     * @param stack 法杖物品栈
     * @param wand  关联的法杖对象
     * @return 有效范围内的槽位索引
     */
    private fun selectedSlot(stack: ItemStack, wand: Wand): Int {
        val size = maxOf(wand.size, wand.magics.size.coerceAtLeast(1))
        return stack.getOrDefault(ModItems.WAND_SELECTED_SLOT, 0).coerceIn(0, size - 1)
    }

    private fun tickSync(stack: ItemStack) {
        val wand = getWand(stack) ?: return
        val size = maxOf(wand.size, wand.magics.size)
        val ids = (0 until size).map { i -> wand.magics.getOrNull(i)?.id ?: "" }
        if (stack.get(ModItems.WAND_SPELLS.get()) != ids) stack.set(ModItems.WAND_SPELLS, ids)
    }
}
