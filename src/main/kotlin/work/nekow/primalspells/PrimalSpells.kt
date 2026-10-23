package work.nekow.primalspells

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger
import work.nekow.primalspells.entity.DroneEntity
import work.nekow.primalspells.entity.RogueMageEntity
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.event.ProjectileShieldHandler
import work.nekow.primalspells.event.ShieldDummyCommand
import work.nekow.primalspells.event.SpellRuntimeEvents
import work.nekow.primalspells.event.SpellcasterSpawns
import work.nekow.primalspells.event.WandCommand
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.integration.PackRules
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.network.ModNetworking
import work.nekow.primalspells.registry.ModDamageTypes
import work.nekow.primalspells.registry.ModEntities
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.registry.ModLoot
import work.nekow.primalspells.registry.ModSpells
import work.nekow.primalspells.wand.WandManager

/**
 * 模组主入口（公共侧，**专用服务器也会加载**）。
 *
 * 职责边界（改动前请先看 `docs/conventions.md`）：
 * - 只做注册（数据组件/物品/方块/实体/伤害类型）、事件总线挂载、网络包注册；
 * - 因此这里**不引用任何客户端类**：需要客户端处理的 S→C 包通过 `ClientPayloadSink` 间接分发，
 *   由 `client/PrimalSpellsClient` 在初始化时安装实现（服务端上保持 null，什么也不做）；
 * - 编杖/编包走原版「物品对上物品」交互（`WandItem`/`SpellPouchItem.overrideOtherStackedOnMe`），
 *   编杖、选槽和预览的请求校验集中在network包。
 */
@Mod(PrimalSpells.MODID)
class PrimalSpells(bus: IEventBus, container: ModContainer) {
    init {
        ModSpells.registerAll()
        NeoForge.EVENT_BUS.register(WandEventHandler)
        // 原版投射物的护盾命中兜底
        NeoForge.EVENT_BUS.register(ProjectileShieldHandler)
        // 测试指令：/primalspells shielddummy [cards|clear]（详见类注释）
        NeoForge.EVENT_BUS.register(ShieldDummyCommand)
        NeoForge.EVENT_BUS.register(WandCommand)
        NeoForge.EVENT_BUS.register(PackRules)
        NeoForge.EVENT_BUS.register(SpellRuntimeEvents)
        NeoForge.EVENT_BUS.register(WandManager)
        NeoForge.EVENT_BUS.register(SpellcasterSpawns)
        ModLoot.SERIALIZERS.register(bus)
        ModItems.COMPONENTS.register(bus)
        ModItems.ITEMS.register(bus)
        ModItems.TABS.register(bus)
        ModDamageTypes.DAMAGE_TYPES.register(bus)
        ModEntities.ENTITY_TYPES.register(bus)

        // 注册实体属性
        bus.addListener<EntityAttributeCreationEvent> { event ->
            event.put(ModEntities.ROGUE_MAGE.get(), RogueMageEntity.createAttributes().build())
            event.put(ModEntities.DRONE.get(), DroneEntity.createAttributes().build())
            event.put(ModEntities.TEST_DUMMY.get(), TestDummyEntity.createAttributes().build())
        }

        bus.addListener<net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent> { event ->
            event.register(ModEntities.ROGUE_MAGE.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RogueMageEntity::canSpawn,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE)
        }

        bus.addListener<RegisterPayloadHandlersEvent>(ModNetworking::register)
    }

    companion object {
        const val MODID = "primalspells"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
