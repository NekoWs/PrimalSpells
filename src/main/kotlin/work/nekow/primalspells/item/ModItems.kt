package work.nekow.primalspells.item

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.item.component.WandID.Companion.TYPE
import work.nekow.primalspells.magic.MagicManager

class ModItems {
    companion object {
        val ITEMS = DeferredRegister.createItems(PrimalSpells.MODID)
        val COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, PrimalSpells.MODID
        )
        val TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PrimalSpells.MODID)

        val MAGICS = hashMapOf<String, DeferredItem<Item>>()

        val PRIMAL_SPELLS_TAB = TABS.register("primalspells") { _ ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.primalspells"))
                .icon { ItemStack(WAND.get()) }
                .displayItems { _, output ->
                    output.accept(WAND)
                    MAGICS.values.forEach {
                        output.accept(it)
                    }
                }
                .build()
        }

        val WAND_ID = COMPONENTS.register("wand_id") { _ -> TYPE }

        val WAND = ITEMS.registerItem("wand") { props ->
            WandItem(props.stacksTo(1))
        }

        init {
            MagicManager.registry.forEach { (key, value) ->
                ITEMS.registerItem(key) { props ->
                    Item(props.stacksTo(1))
                }.also { item ->
                    MAGICS[key] = item
                }
            }
        }
    }
}