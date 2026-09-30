package work.nekow.primalspells

import com.mojang.logging.LogUtils
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger
import work.nekow.primalspells.entity.DroneEntity
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.event.ShieldDummyCommand
import work.nekow.primalspells.event.WandCommand
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.network.ClientPayloadSink
import work.nekow.primalspells.network.ScreenShakePayload
import work.nekow.primalspells.network.SelectSpellSlotPayload
import work.nekow.primalspells.network.SyncWandStatsPayload
import work.nekow.primalspells.registry.ModDamageTypes
import work.nekow.primalspells.registry.ModEntities
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.WandManager

/**
 * 模组主入口（公共侧，**专用服务器也会加载**）。
 *
 * 职责边界（改动前请先看 `docs/conventions.md`）：
 * - 只做注册（数据组件/物品/方块/实体/伤害类型）、事件总线挂载、网络包注册；
 * - 因此这里**不引用任何客户端类**：需要客户端处理的 S→C 包通过 [ClientPayloadSink] 间接分发，
 *   由 `client/PrimalSpellsClient` 在初始化时安装实现（服务端上保持 null，什么也不做）；
 * - 编杖/编包走原版「物品对上物品」交互（`WandItem`/`SpellPouchItem.overrideOtherStackedOnMe`），
 *   这里只为滚轮选槽保留一个 C→S 包。
 */
@Mod(PrimalSpells.MODID)
class PrimalSpells(bus: IEventBus, container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.register(WandEventHandler)
        // 测试指令：/primalspells shielddummy [cards|clear]（详见类注释）
        NeoForge.EVENT_BUS.register(ShieldDummyCommand)
        NeoForge.EVENT_BUS.register(WandCommand)
        NeoForge.EVENT_BUS.register(MagicManager)
        NeoForge.EVENT_BUS.register(WandManager)
        ModItems.COMPONENTS.register(bus)
        ModItems.ITEMS.register(bus)
        ModItems.TABS.register(bus)
        ModDamageTypes.DAMAGE_TYPES.register(bus)
        ModEntities.ENTITY_TYPES.register(bus)

        // 注册实体属性
        bus.addListener<EntityAttributeCreationEvent> { event ->
            event.put(ModEntities.DRONE.get(), DroneEntity.createAttributes().build())
            event.put(ModEntities.TEST_DUMMY.get(), TestDummyEntity.createAttributes().build())
        }

        bus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar("1.0.0")

            // S→C：交给客户端安装的处理器（服务端上为 null）
            registrar.playToClient(
                SyncWandStatsPayload.ID,
                SyncWandStatsPayload.STREAM_CODEC
            ) { payload, _ ->
                ClientPayloadSink.wandStats?.invoke(payload)
            }
            // 镜头冲击：特效重音那一拍的一条 8 字节包，逐帧偏移在客户端本地算
            registrar.playToClient(
                ScreenShakePayload.ID,
                ScreenShakePayload.STREAM_CODEC
            ) { payload, _ ->
                ClientPayloadSink.screenShake?.invoke(payload)
            }

            // C→S：滚轮选槽（法杖与小包共用）
            registrar.playToServer(
                SelectSpellSlotPayload.ID,
                SelectSpellSlotPayload.STREAM_CODEC
            ) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@playToServer
                val inv = player.inventory
                for (i in 0 until inv.containerSize) {
                    val stack = inv.getItem(i)
                    val matched = if (payload.pouch) {
                        stack.item is SpellPouchItem && SpellPouchItem.getPouchId(stack) == payload.targetId
                    } else {
                        stack.item is WandItem && stack.get(ModItems.WAND_ID)?.wandId == payload.targetId
                    }
                    if (matched) {
                        if (payload.pouch) {
                            stack.set(ModItems.POUCH_SELECTED_SLOT.get(), payload.slot)
                        } else {
                            stack.set(ModItems.WAND_SELECTED_SLOT.get(), payload.slot)
                        }
                        break
                    }
                }
            }
        }
    }

    companion object {
        const val MODID = "primalspells"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
