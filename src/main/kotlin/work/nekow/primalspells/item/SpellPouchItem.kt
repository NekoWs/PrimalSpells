package work.nekow.primalspells.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import work.nekow.primalspells.item.component.WandID
import work.nekow.primalspells.registry.ModItems
import java.util.UUID

/**
 * 法术小包：可收纳法术物品的容器（非方块），内容存于 [ModItems.SPELL_POUCH_CONTENTS] 组件
 * （指向法术 id 的列表，固定 [SLOT_COUNT] 格）。
 *
 * **操作逻辑与法杖一致**（走原版「物品对上物品」交互，没有自定义界面）：
 * - 光标拿着法术物品右键小包 → 放进当前选中槽，该槽原有法术回到光标（交换）
 * - 光标为空时右键小包 → 取出当前选中槽的法术到光标
 * - 容器界面里鼠标悬停小包时滚轮 → 切换选中槽（见 `client/input/SpellSlotScrollHandler`）
 * - 选中槽由 [ModItems.POUCH_SELECTED_SLOT] 组件保存，服务端权威
 *
 * 小包以 [ModItems.POUCH_ID] 组件唯一标识（首次进入背包时由 [inventoryTick] 生成）：
 * 组件随物品同步，客户端据此做 tooltip 高亮与选槽请求。
 */
class SpellPouchItem(properties: Properties) : Item(properties) {

    /** 小包不在世界里使用（没有右键界面），编辑一律发生在物品栏槽位上。 */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult =
        InteractionResult.PASS

    /** 首次进入背包时补一个唯一 id（本版本的 `inventoryTick` 只在服务端触发）。 */
    override fun inventoryTick(stack: ItemStack, level: ServerLevel, owner: Entity, slot: EquipmentSlot?) {
        ensurePouchId(stack)
    }

    override fun overrideOtherStackedOnMe(
        self: ItemStack, other: ItemStack, slot: Slot,
        action: ClickAction, player: Player, carried: SlotAccess
    ): Boolean {
        if (action != ClickAction.SECONDARY) return false
        val index = getSelectedSlot(self)

        if (!other.isEmpty) {
            // 放入：只接受法术物品；目标槽原有法术回到光标
            val spellId = ModItems.spellIdOf(other.item) ?: return false
            val existing = getSpells(self).getOrNull(index).orEmpty()
            setSpell(self, index, spellId)
            carried.set(stackOf(existing))
            return true
        }

        // 取出：把选中槽的法术放到光标（空槽交回原版处理）
        val existing = getSpells(self).getOrNull(index).orEmpty()
        if (existing.isEmpty()) return false
        setSpell(self, index, "")
        carried.set(stackOf(existing))
        return true
    }

    companion object {
        /** 小包固定容量（与 tooltip 网格一致） */
        const val SLOT_COUNT = 16

        /** 读取小包内的法术 id 列表 */
        fun getSpells(stack: ItemStack): List<String> =
            stack.get(ModItems.SPELL_POUCH_CONTENTS.get()) ?: emptyList()

        /** 读取小包唯一 id（未初始化时 null） */
        fun getPouchId(stack: ItemStack): String? =
            stack.get(ModItems.POUCH_ID.get())?.wandId

        /** 生成并写入小包唯一 id（服务端调用，组件随物品同步） */
        fun ensurePouchId(stack: ItemStack): String {
            getPouchId(stack)?.let { return it }
            val id = UUID.randomUUID().toString()
            stack.set(ModItems.POUCH_ID.get(), WandID(id))
            return id
        }

        /** 读取当前选中的槽位（越界自动收敛到合法范围） */
        fun getSelectedSlot(stack: ItemStack): Int =
            (stack.get(ModItems.POUCH_SELECTED_SLOT.get()) ?: 0).coerceIn(0, SLOT_COUNT - 1)

        /**
         * 写入第 [index] 个槽位的法术 id（空串 = 清空），并把长度恰为 [SLOT_COUNT] 的列表回写组件。
         * 越界索引直接忽略，保证组件长度始终等于容量（旧实现按需 grow，读档后槽位会整体前移）。
         */
        fun setSpell(stack: ItemStack, index: Int, spellId: String) {
            if (index !in 0 until SLOT_COUNT) return
            val list = getSpells(stack).toMutableList()
            while (list.size < SLOT_COUNT) list.add("")
            list[index] = spellId
            stack.set(ModItems.SPELL_POUCH_CONTENTS.get(), list)
        }

        /** 法术 id → 单个物品栈（id 为空或未注册时返回空栈） */
        private fun stackOf(spellId: String): ItemStack {
            if (spellId.isEmpty()) return ItemStack.EMPTY
            val item = ModItems.spellItem(spellId) ?: return ItemStack.EMPTY
            return ItemStack(item)
        }
    }
}
