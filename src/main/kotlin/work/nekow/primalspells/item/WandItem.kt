package work.nekow.primalspells.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.PrimalSpells.Companion.debug
import work.nekow.primalspells.magic.Fireball
import work.nekow.primalspells.wand.WandManager

class WandItem(properties: Properties) : Item(properties) {
    override fun inventoryTick(
        stack: ItemStack,
        level: ServerLevel,
        owner: Entity,
        slot: EquipmentSlot?
    ) {
        if (!stack.has(ModItems.WAND_ID))
            WandManager.createWand(stack)
    }

    fun spell(caster: Entity, stack: ItemStack) {
        val id = stack.get(ModItems.WAND_ID) ?: return
        val wand = WandManager[id]
        if (caster.isShiftKeyDown) {
            wand.magics.add(Fireball())
            wand.load()
            caster.debug("Added Fireball!")
            caster.debug("Magics: %s", wand.magics.joinToString { it.javaClass.simpleName })
            return
        }
        wand.spell(caster)
    }
}

