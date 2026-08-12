package work.nekow.primalspells.block

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells

object ModBlocks {
    val BLOCKS = DeferredRegister.createBlocks(PrimalSpells.MODID)

    val WAND_EDITING_TABLE: DeferredBlock<WandEditingTableBlock> = BLOCKS.register("wand_editing_table") { key ->
        WandEditingTableBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD).noLootTable().setId(ResourceKey.create(Registries.BLOCK, key)))
    }
}
