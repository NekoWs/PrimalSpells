package work.nekow.primalspells.event

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import work.nekow.primalspells.item.WandItem

/**
 * 法杖相关的**公共侧**事件处理（专用服务器也会加载）。
 *
 * 行为约定（已实机确认）：
 * - 法杖**不与方块/实体交互**：右键方块、右键生物的原版交互一律取消；
 * - 取消结果显式设为 [InteractionResult.PASS]，让流程回落到 `Item.use`，
 *   于是「对着方块或生物长按右键」依然能正常进入长按施法（否则会完全没反应）；
 * - 玩家登出时清掉该玩家的施法记录，避免静态表随玩家数量只增不减。
 *
 * 纯客户端的输入/表现（滚轮选槽、tooltip、HUD）不在这里：
 * 见 `client/input/SpellSlotScrollHandler` 与 `client/tooltip`、`client/hud` 两个包。
 */
object WandEventHandler {

    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.itemStack.item !is WandItem) return
        event.isCanceled = true
        event.setCancellationResult(InteractionResult.PASS)
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.itemStack.item !is WandItem) return
        event.isCanceled = true
        event.setCancellationResult(InteractionResult.PASS)
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.item is WandItem) {
                WandItem.syncStats(stack, player)
            }
        }
    }

    @SubscribeEvent
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        // 施法记录按玩家 UUID 缓存在静态表里，登出即清理
        WandItem.forgetCastState(player.uuid)
    }
}
