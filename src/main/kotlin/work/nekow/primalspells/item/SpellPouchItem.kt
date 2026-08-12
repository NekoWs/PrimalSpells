package work.nekow.primalspells.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.component.WandID
import java.util.*

/**
 * 法术小包：可收纳法术物品的容器（非方块）。
 * 内容存于 [ModItems.SPELL_POUCH_CONTENTS] 组件（法术 id 列表）。
 * 右键打开悬浮窗进行管理（见 ui.pouch.SpellPouchWindow）。
 *
 * 小包以 [ModItems.POUCH_ID] 组件唯一标识：容器菜单（背包/小包 UI）切换时
 * 槽位索引会失效，所有定位一律按 pouchId 进行（服务端在玩家背包中查找）。
 */
class SpellPouchItem(properties: Properties) : Item(properties) {

    companion object {
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
    }
}
