package work.nekow.primalspells.client

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.item.ModMenus
import work.nekow.primalspells.client.entity.CubeEntityModel
import work.nekow.primalspells.client.entity.CubeEntityRenderer
import work.nekow.primalspells.client.entity.DroneEntityModel
import work.nekow.primalspells.client.entity.DroneEntityRenderer
import work.nekow.primalspells.client.entity.SphereEntityModel
import work.nekow.primalspells.client.entity.SphereEntityRenderer
import work.nekow.primalspells.entity.ModEntities
import work.nekow.nekoui.FloatingWindowManager
import work.nekow.nekoui.UiReloadHandler
import work.nekow.nekoui.pouch.PouchWindowManager

@Mod(value = PrimalSpells.MODID, dist = [Dist.CLIENT])
class PrimalSpellsClient(bus: IEventBus, container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.register(ClientTooltipHandler)
        NeoForge.EVENT_BUS.register(UiReloadHandler)
        NeoForge.EVENT_BUS.register(FloatingWindowManager)
        NeoForge.EVENT_BUS.register(PouchWindowManager)
        NeoForge.EVENT_BUS.register(ClientContainerDragHandler)

        bus.addListener<RegisterClientTooltipComponentFactoriesEvent> { event ->
            event.register(WandSpellTooltip::class.java, ::ClientWandSpellTooltip)
            event.register(PouchTooltip::class.java, ::ClientPouchTooltip)
        }

        // 注册法术小包容器界面
        bus.addListener<RegisterMenuScreensEvent> { event ->
            event.register(ModMenus.SPELL_POUCH.get(), ::SpellPouchScreen)
        }

        // 注册实体渲染器
        bus.addListener<EntityRenderersEvent.RegisterRenderers> { event ->
            event.registerEntityRenderer(ModEntities.CUBE.get(), ::CubeEntityRenderer)
            event.registerEntityRenderer(ModEntities.SPHERE.get(), ::SphereEntityRenderer)
            event.registerEntityRenderer(ModEntities.DRONE.get(), ::DroneEntityRenderer)
        }

        // 注册实体模型层定义
        bus.addListener<EntityRenderersEvent.RegisterLayerDefinitions> { event ->
            event.registerLayerDefinition(CubeEntityRenderer.CUBE_LAYER, CubeEntityModel::createBodyLayer)
            event.registerLayerDefinition(SphereEntityRenderer.SPHERE_LAYER, SphereEntityModel::createBodyLayer)
            event.registerLayerDefinition(DroneEntityRenderer.DRONE_LAYER, DroneEntityModel::createBodyLayer)
        }
    }
}
