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
import work.nekow.primalspells.event.ProjectileShieldHandler
import work.nekow.primalspells.event.ShieldDummyCommand
import work.nekow.primalspells.event.WandCommand
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.network.WandCastPayload
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
        // 原版投射物的护盾兜底（第 40 轮：护盾能防御/反弹箭矢、火球…）
        NeoForge.EVENT_BUS.register(ProjectileShieldHandler)
        // 测试指令：/primalspells shielddummy [cards|clear]（详见类注释）
        NeoForge.EVENT_BUS.register(ShieldDummyCommand)
        NeoForge.EVENT_BUS.register(WandCommand)
        NeoForge.EVENT_BUS.register(MagicManager)
        NeoForge.EVENT_BUS.register(WandManager)
        NeoForge.EVENT_BUS.register(work.nekow.primalspells.event.SpellcasterSpawns)
        work.nekow.primalspells.registry.ModLoot.SERIALIZERS.register(bus)
        ModItems.COMPONENTS.register(bus)
        ModItems.ITEMS.register(bus)
        ModItems.TABS.register(bus)
        ModDamageTypes.DAMAGE_TYPES.register(bus)
        ModEntities.ENTITY_TYPES.register(bus)

        // 注册实体属性
        bus.addListener<EntityAttributeCreationEvent> { event ->
            event.put(ModEntities.ROGUE_MAGE.get(), work.nekow.primalspells.entity.RogueMageEntity.createAttributes().build())
            event.put(ModEntities.DRONE.get(), DroneEntity.createAttributes().build())
            event.put(ModEntities.TEST_DUMMY.get(), TestDummyEntity.createAttributes().build())
        }

        bus.addListener<net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent> { event ->
            event.register(ModEntities.ROGUE_MAGE.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                work.nekow.primalspells.entity.RogueMageEntity::canSpawn,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE)
        }

        bus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar("1.2.0")

            // S→C：交给客户端安装的处理器（服务端上为 null）
            registrar.playToClient(
                SyncWandStatsPayload.ID,
                SyncWandStatsPayload.STREAM_CODEC
            ) { payload, _ ->
                ClientPayloadSink.wandStats?.invoke(payload)
            }
            registrar.playToClient(WandCastPayload.ID, WandCastPayload.STREAM_CODEC) { payload, _ ->
                ClientPayloadSink.wandCast?.invoke(payload)
            }
            registrar.playToClient(work.nekow.primalspells.network.HudImpactPayload.ID,
                work.nekow.primalspells.network.HudImpactPayload.STREAM_CODEC) { payload, _ ->
                ClientPayloadSink.hudImpact?.invoke(payload)
            }
            // 镜头冲击：特效重音那一拍的一条 8 字节包，逐帧偏移在客户端本地算
            registrar.playToClient(
                ScreenShakePayload.ID,
                ScreenShakePayload.STREAM_CODEC
            ) { payload, _ ->
                ClientPayloadSink.screenShake?.invoke(payload)
            }

            val previewCodec = work.nekow.primalspells.network.WandPreviewPayload.STREAM_CODEC
            registrar.playBidirectional(work.nekow.primalspells.network.WandPreviewPayload.ID, previewCodec) { payload, context ->
                if (context.player() is ServerPlayer) {
                    val player = context.player() as ServerPlayer
                    val now = player.level().gameTime
                    val data = player.persistentData
                    val last = data.getLongOr("primalspells:preview_at", -100L)
                    if (payload.preview == null && now - last >= 5) {
                        data.putLong("primalspells:preview_at", now)
                        val stacks = player.containerMenu.slots.map { it.item } + player.containerMenu.carried
                        val stack = stacks.firstOrNull { it.item is WandItem && it.get(ModItems.WAND_ID)?.wandId == payload.wandId }
                        val wand = stack?.let(WandItem::getWand)
                        if (wand != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            work.nekow.primalspells.network.WandPreviewPayload(payload.wandId, wand.preview()))
                    }
                } else ClientPayloadSink.wandPreview?.invoke(payload)
            }

            registrar.playToServer(work.nekow.primalspells.network.EditWandPayload.ID,
                work.nekow.primalspells.network.EditWandPayload.STREAM_CODEC) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@playToServer
                val menu = player.containerMenu
                if (menu.containerId != payload.menuId) return@playToServer
                val slot = menu.slots.getOrNull(payload.slot) ?: return@playToServer
                val stack = slot.item
                val item = stack.item as? WandItem ?: return@playToServer
                if (!slot.mayPickup(player) || stack.get(ModItems.WAND_ID)?.wandId != payload.wandId) return@playToServer
                val wand = WandItem.getWand(stack) ?: return@playToServer
                if (payload.spellSlot !in 0 until maxOf(wand.size, wand.magics.size)) return@playToServer
                // 创造物品栏的光标按原版规则由客户端管理；只允许已注册散卡，生存不接受此通路。
                if (payload.creativeCount >= 0) {
                    if (!player.isCreative) return@playToServer
                    if (payload.creativeCount == 0 && payload.creativeSpell.isEmpty()) menu.carried = net.minecraft.world.item.ItemStack.EMPTY
                    else {
                        val spellItem = ModItems.spellItem(payload.creativeSpell) ?: return@playToServer
                        val cursor = net.minecraft.world.item.ItemStack(spellItem)
                        if (payload.creativeCount !in 1..cursor.maxStackSize) return@playToServer
                        cursor.count = payload.creativeCount; menu.carried = cursor
                    }
                }
                if (!menu.carried.isEmpty && ModItems.spellIdOf(menu.carried.item) == null) return@playToServer
                stack.set(ModItems.WAND_SELECTED_SLOT, payload.spellSlot)
                item.overrideOtherStackedOnMe(stack, menu.carried, slot, net.minecraft.world.inventory.ClickAction.SECONDARY,
                    player, object : net.minecraft.world.entity.SlotAccess {
                        override fun get() = menu.carried
                        override fun set(value: net.minecraft.world.item.ItemStack): Boolean { menu.carried = value; return true }
                    })
                // 创造界面刻意忽略原版单独光标包；完整菜单同步包含权威光标。
                menu.broadcastFullState()
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    work.nekow.primalspells.network.WandPreviewPayload(payload.wandId, wand.preview()))
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
