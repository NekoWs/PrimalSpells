package work.nekow.primalspells.item

import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack

/**
 * 法术小包容器：16 个法术槽位，物品变化时写回小包的 [ModItems.SPELL_POUCH_CONTENTS] 组件。
 */
class SpellPouchInventory(
    private val pouchStack: ItemStack,
) : SimpleContainer(SLOT_COUNT) {

    init {
        val spells = SpellPouchItem.getSpells(pouchStack)
        for (i in 0 until SLOT_COUNT) {
            val id = spells.getOrNull(i)
            val item = id?.takeIf { it.isNotEmpty() }?.let { ModItems.MAGICS[it]?.get() }
            super.setItem(i, if (item != null) ItemStack(item) else ItemStack.EMPTY)
        }
    }

    override fun setItem(index: Int, stack: ItemStack) {
        super.setItem(index, stack)
        writeBack()
    }

    private fun writeBack() {
        val ids = (0 until SLOT_COUNT).map { i ->
            val st = getItem(i)
            if (st.isEmpty) "" else {
                ModItems.MAGICS.entries.firstOrNull { it.value.get() == st.item }?.key ?: ""
            }
        }
        pouchStack.set(ModItems.SPELL_POUCH_CONTENTS.get(), ids)
    }

    companion object {
        const val SLOT_COUNT = 16
    }
}
