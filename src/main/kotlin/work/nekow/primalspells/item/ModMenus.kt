package work.nekow.primalspells.item

import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells

/** 容器菜单注册 */
object ModMenus {
    val MENU_TYPES = DeferredRegister.create(Registries.MENU, PrimalSpells.MODID)

    /** 法术小包容器菜单（extraData 携带小包在容器中的槽位索引） */
    val SPELL_POUCH = MENU_TYPES.register("spell_pouch") { _ ->
        IMenuTypeExtension.create { containerId, inventory, data ->
            val pouchSlot = data.readVarInt()
            val pouchStack = inventory.player.containerMenu.getSlot(pouchSlot).item
            SpellPouchMenu(containerId, inventory, SpellPouchInventory(pouchStack))
        }
    }
}
