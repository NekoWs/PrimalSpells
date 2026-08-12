package work.nekow.primalspells.item

import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.block.ModBlocks
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
                    output.accept(WAND_EDITING_TABLE_ITEM)
                    output.accept(SPELL_POUCH)
                    MAGICS.values.forEach { output.accept(it) }
                }
                .build()
        }

        val WAND_ID = COMPONENTS.register("wand_id") { _ -> TYPE }

        /** 法术小包唯一标识（复用 WandID 编解码，但必须独立 DataComponentType 实例） */
        val POUCH_ID = COMPONENTS.register("pouch_id") { _ ->
            DataComponentType.builder<work.nekow.primalspells.item.component.WandID>()
                .persistent(work.nekow.primalspells.item.component.WandID.CODEC)
                .networkSynchronized(work.nekow.primalspells.item.component.WandID.STREAM_CODEC)
                .cacheEncoding()
                .build()
        }

        val WAND_SPELLS = COMPONENTS.register("wand_spells") { _ ->
            DataComponentType.builder<List<String>>()
                .persistent(Codec.STRING.listOf())
                .networkSynchronized(listStreamCodec())
                .cacheEncoding()
                .build()
        }

        val WAND_SELECTED_SLOT = COMPONENTS.register("wand_selected_slot") { _ ->
            DataComponentType.builder<Int>()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .cacheEncoding()
                .build()
        }

        val SPELL_POUCH_CONTENTS = COMPONENTS.register("spell_pouch_contents") { _ ->
            DataComponentType.builder<List<String>>()
                .persistent(Codec.STRING.listOf())
                .networkSynchronized(listStreamCodec())
                .cacheEncoding()
                .build()
        }

        val WAND = ITEMS.registerItem("wand") { props ->
            WandItem(props.stacksTo(1))
        }

        val SPELL_POUCH = ITEMS.registerItem("spell_pouch") { props ->
            SpellPouchItem(props.stacksTo(1))
        }

        val WAND_EDITING_TABLE_ITEM = ITEMS.registerSimpleBlockItem("wand_editing_table", ModBlocks.WAND_EDITING_TABLE)

        init {
            MagicManager.registry.forEach { (key, _) ->
                ITEMS.registerItem(key) { props ->
                    Item(props.stacksTo(1))
                }.also { item ->
                    MAGICS[key] = item
                }
            }
        }

        private fun listStreamCodec(): StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, List<String>> {
            return object : StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, List<String>> {
                override fun decode(buf: net.minecraft.network.RegistryFriendlyByteBuf): List<String> {
                    val size = buf.readVarInt()
                    return (0 until size).map { buf.readUtf() }
                }
                override fun encode(buf: net.minecraft.network.RegistryFriendlyByteBuf, value: List<String>) {
                    buf.writeVarInt(value.size)
                    value.forEach { buf.writeUtf(it) }
                }
            }
        }
    }
}
