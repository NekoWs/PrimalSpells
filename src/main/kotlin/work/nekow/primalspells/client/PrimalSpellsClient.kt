package work.nekow.primalspells.client

import net.minecraft.client.renderer.entity.ArmorStandRenderer
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import work.nekow.particledrawing.api.ParticleManager
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.client.hud.WandHudRenderer
import work.nekow.primalspells.client.input.ClientContainerDragHandler
import work.nekow.primalspells.client.input.SpellSlotScrollHandler
import work.nekow.primalspells.client.render.DroneEntityModel
import work.nekow.primalspells.client.render.DroneEntityRenderer
import work.nekow.primalspells.client.render.ParticleTextures
import work.nekow.primalspells.client.tooltip.ClientPouchTooltip
import work.nekow.primalspells.client.tooltip.ClientTooltipHandler
import work.nekow.primalspells.client.capture.CaptureDriver
import work.nekow.primalspells.client.tooltip.ClientWandSpellTooltip
import work.nekow.primalspells.client.tooltip.PouchTooltip
import work.nekow.primalspells.client.tooltip.SpellSlotTracker
import work.nekow.primalspells.client.tooltip.WandSpellTooltip
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.network.ClientPayloadSink
import work.nekow.primalspells.registry.ModEntities

/**
 * 客户端入口（`@Mod(dist = CLIENT)`，专用服务器不加载本类）。
 *
 * 这里集中做三件事：
 * 1. 给公共侧网络包安装客户端处理器（[ClientPayloadSink]）——公共入口因此不必引用客户端类；
 * 2. 注册纯客户端功能：tooltip 组装/工厂、容器输入（滚轮选槽、Ctrl 拖拽交换）、实体渲染器与模型层；
 * 3. 退出世界/断线时清理按 id 缓存的客户端状态（HUD 数值、护盾、选槽预览）。
 */
@Mod(value = PrimalSpells.MODID, dist = [Dist.CLIENT])
class PrimalSpellsClient(bus: IEventBus, container: ModContainer) {
    /** 内置形状贴图只在首个客户端 tick 里确保一次（见 init 里的说明） */
    private var builtinsReady = false

    init {
        // 1. 网络包的客户端处理器
        ClientPayloadSink.wandStats = { WandHudRenderer.putStats(it) }
        ClientPayloadSink.screenShake = { ScreenShakeClient.onShake(it) }

        // 2. 实机录制驱动（开发期自动化测试的「眼睛」）：
        //    只在「非生产环境 + 显式系统属性 primalspells.capture」下注册；
        //    生产包里 shouldInstall() 恒为 false，CaptureDriver 整个类不会被加载。
        if (CaptureDriver.shouldInstall()) CaptureDriver.install()

        // 3a. ParticleDrawing 的内置形状贴图（SOFT_DOT / LINE）：客户端按需生成、幂等。
        //     专用服务器上服务端贴图注册表里没有内置名，载荷带的就是**内联名字**，
        //     所以客户端必须确保它们已经生成，否则会回落纯白方块。
        //     ⚠️ 不能放在 `FMLClientSetupEvent`：那时渲染线程还没就绪，
        //     `RenderSystem` 会抛「Rendersystem called from wrong thread」把客户端打崩（第 29 轮踩过）。
        //     放到**首个客户端 tick**（渲染线程已就绪），一个布尔量保证只做一次。
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> {
            if (!builtinsReady) {
                builtinsReady = true
                ParticleManager.registerBuiltinTextures()
                ParticleTextures.register()
            }
        }

        // 3. 纯客户端事件与功能注册
        ScreenShakeClient.install()
        NeoForge.EVENT_BUS.register(ClientTooltipHandler)
        NeoForge.EVENT_BUS.register(ClientContainerDragHandler)
        NeoForge.EVENT_BUS.register(SpellSlotScrollHandler)

        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> {
            // 4. 清理只增不减的客户端缓存
            WandHudRenderer.clear()
            SpellSlotTracker.clear()
        }

        bus.addListener<RegisterClientTooltipComponentFactoriesEvent> { event ->
            event.register(WandSpellTooltip::class.java, ::ClientWandSpellTooltip)
            event.register(PouchTooltip::class.java, ::ClientPouchTooltip)
        }

        bus.addListener<EntityRenderersEvent.RegisterRenderers> { event ->
            event.registerEntityRenderer(ModEntities.DRONE.get(), ::DroneEntityRenderer)
            // 测试木桩直接复用原版盔甲架渲染器（TestDummyEntity 是 ArmorStand 子类，
            // 泛型不匹配，运行时安全，需显式转换）
            @Suppress("UNCHECKED_CAST")
            event.registerEntityRenderer(ModEntities.TEST_DUMMY.get()) { ctx ->
                ArmorStandRenderer(ctx) as
                    EntityRenderer<TestDummyEntity, ArmorStandRenderState>
            }
        }

        bus.addListener<EntityRenderersEvent.RegisterLayerDefinitions> { event ->
            event.registerLayerDefinition(DroneEntityRenderer.DRONE_LAYER, DroneEntityModel::createBodyLayer)
        }
    }
}
