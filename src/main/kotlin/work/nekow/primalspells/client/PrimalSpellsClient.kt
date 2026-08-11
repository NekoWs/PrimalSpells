package work.nekow.primalspells.client

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.client.entity.CubeEntityModel
import work.nekow.primalspells.client.entity.CubeEntityRenderer
import work.nekow.primalspells.client.entity.DroneEntityModel
import work.nekow.primalspells.client.entity.DroneEntityRenderer
import work.nekow.primalspells.client.entity.SphereEntityModel
import work.nekow.primalspells.client.entity.SphereEntityRenderer
import work.nekow.primalspells.entity.ModEntities

@Mod(value = PrimalSpells.MODID, dist = [Dist.CLIENT])
class PrimalSpellsClient(bus: IEventBus, container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.register(ClientTooltipHandler)

        bus.addListener<RegisterClientTooltipComponentFactoriesEvent> { event ->
            event.register(WandSpellTooltip::class.java, ::ClientWandSpellTooltip)
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
