package work.nekow.primalspells.item

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.item.component.WandID.Companion.TYPE

class ModItems {
    companion object {
        val ITEMS = DeferredRegister.createItems(PrimalSpells.MODID)
        val COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, PrimalSpells.MODID
        )
        val TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PrimalSpells.MODID)

        val PRIMAL_SPELLS_TAB = TABS.register("primalspells") { _ ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.primalspells"))
                .icon { ItemStack(WAND.get()) }
                .displayItems { _, output ->
                    output.accept(WAND.get())
                    output.accept(FIREBALL.get())
                }
                .build()
        }

        val WAND_ID = COMPONENTS.register("wand_id") { _ -> TYPE }

        val WAND = ITEMS.registerItem("wand") { props ->
            WandItem(props.stacksTo(1))
        }

        val FIREBALL = ITEMS.registerItem("fireball") { props ->
            Item(props.stacksTo(1))
        }
    }
}