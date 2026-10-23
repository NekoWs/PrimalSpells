package work.nekow.primalspells.registry

import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
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
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.TestDummyItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.item.component.WandID.Companion.TYPE
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.wand.WandTier
import work.nekow.primalspells.wand.WandVariant

class ModItems {
    companion object {
        val ITEMS = DeferredRegister.createItems(PrimalSpells.MODID)
        val COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, PrimalSpells.MODID
        )
        val TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PrimalSpells.MODID)

        val MAGICS = hashMapOf<String, DeferredItem<Item>>()

        /** 根据法术 id 获取对应物品（未注册返回 null） */
        fun spellItem(id: String): Item? = MAGICS[id]?.get()

        /** 根据物品获取对应法术 id（非魔法物品返回 null） */
        fun spellIdOf(item: Item): String? =
            MAGICS.entries.firstOrNull { it.value.get() == item }?.key

        val PRIMAL_SPELLS_TAB = TABS.register("primalspells") { _ ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.primalspells"))
                .icon { ItemStack(WAND.get()) }
                .displayItems { _, output ->
                    output.accept(WAND)
                    // 六级法杖：每级两项（普通 / 蓄能型），方便直接拿来对比
                    WandTier.LADDER.forEach { tier ->
                        output.accept(wandStack(tier, WandVariant.NORMAL))
                        output.accept(wandStack(tier, WandVariant.CHARGED))
                    }
                    output.accept(SPELL_COMPASS)
                    output.accept(SPELL_POUCH)
                    output.accept(TEST_DUMMY)
                    // 同类沿注册顺序排列，避免HashMap导致每种法术散落在不同位置。
                    SpellRegistry.factories.entries.sortedBy { (_, factory) ->
                        val magic = factory()
                        when {
                            magic.needInventory -> 4
                            magic is work.nekow.primalspells.magic.core.DrawAction -> 3
                            magic is work.nekow.primalspells.magic.core.Modifier -> 2
                            magic is work.nekow.primalspells.magic.projectile.Projectile && magic.speed == 0f -> 1
                            else -> 0
                        }
                    }.forEach { (id, _) -> MAGICS[id]?.let { output.accept(it) } }
                }
                .build()
        }

        val SPELL_COMPASS = ITEMS.registerItem("spell_compass") { props -> work.nekow.primalspells.item.SpellCompassItem(props.stacksTo(1)) }

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

        /** 蓄能型变体标记（见 [WandVariant.CHARGED]）：打在物品栈上，创建法杖时读一次。 */
        val WAND_CHARGED = COMPONENTS.register("wand_charged") { _ ->
            DataComponentType.builder<Boolean>()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
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

        /** 小包当前选中的法术槽位（与法杖的 [WAND_SELECTED_SLOT] 同语义：滚轮选槽 + 物品对物品装卸） */
        val POUCH_SELECTED_SLOT = COMPONENTS.register("pouch_selected_slot") { _ ->
            DataComponentType.builder<Int>()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .cacheEncoding()
                .build()
        }

        /**
         * **调试杖**（3000 法力 / 1000 回蓝 / 0 双延迟 / 18 槽）：开发期用来隔离变量的那根，
         * 不在六级曲线里，正常玩法拿不到（创造模式物品栏 + `/primalspells wand debug`）。
         */
        val WAND = ITEMS.registerItem("wand") { props ->
            WandItem(props.stacksTo(1))
        }

        /**
         * **六级法杖物品**（与 [WandTier.LADDER] 一一对应，注册名 = `WandTier.itemName`）。
         *
         * 数值不在物品上：物品只决定"是哪一级"，真正的数值由 `WandManager.createWand` 在**首次 tick**
         * 时按等级掷出（[work.nekow.primalspells.wand.WandStats.roll]）并落进法杖实例、随 NBT 存档。
         * 见 [docs/design/wand-tiers.md]。
         */
        val WAND_TIERS = WandTier.LADDER.associateWith { tier ->
            ITEMS.registerItem(tier.itemName) { props ->
                WandItem(props.stacksTo(1))
            }
        }

        /** 物品 → 等级（含调试杖）；不是法杖物品时返回 null。创建法杖时用它决定数值。 */
        fun wandTierOf(item: Item): WandTier? =
            WAND_TIERS.entries.firstOrNull { it.value.get() === item }?.key
                ?: if (item === WAND.get()) WandTier.DEBUG else null

        /**
         * 造一根**指定等级 + 变体**的法杖物品栈。
         *
         * 蓄能型（[WandVariant.CHARGED]）用组件 + 自定义名表达（"大师杖·蓄能"），
         * 数值仍由创建时统一掷出 —— 这样掉落/指令/创造模式三条路只有一套逻辑。
         */
        fun wandStack(tier: WandTier, variant: WandVariant = WandVariant.NORMAL): ItemStack {
            val stack = ItemStack(WAND_TIERS[tier]?.get() ?: WAND.get())
            if (variant != WandVariant.NORMAL && variant.suffixKey != null) {
                stack.set(WAND_CHARGED, true)
                stack.set(
                    DataComponents.CUSTOM_NAME,
                    Component.translatable(variant.suffixKey!!, Component.translatable(tier.displayKey))
                )
            }
            return stack
        }

        val SPELL_POUCH = ITEMS.registerItem("spell_pouch") { props ->
            SpellPouchItem(props.stacksTo(1))
        }

        val TEST_DUMMY = ITEMS.registerItem("test_dummy") { props ->
            TestDummyItem(props.stacksTo(16))
        }

        init {
            SpellRegistry.factories.forEach { (key, _) ->
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
