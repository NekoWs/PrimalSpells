package work.nekow.primalspells.block

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import work.nekow.primalspells.ui.UiScreen

class WandEditingTableBlock(properties: Properties) : Block(properties) {

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) {
            UiScreen.openHtml(
                Component.translatable("block.primalspells.wand_editing_table"),
                "wand_editing_table", 0,
                clicks = mapOf(
                    "test" to { s: UiScreen -> s.close() }
                )
            )
        }
        return InteractionResult.SUCCESS
    }
}
