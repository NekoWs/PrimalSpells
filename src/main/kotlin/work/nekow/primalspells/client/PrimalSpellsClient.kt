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
import work.nekow.primalspells.client.render.RogueMageRenderer
import work.nekow.primalspells.client.render.DroneEntityRenderer
import work.nekow.primalspells.client.render.ParticleTextures
import work.nekow.primalspells.client.render.WandHandPose
import work.nekow.primalspells.client.render.WandModelProperty
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent
import net.minecraft.resources.Identifier
import work.nekow.primalspells.registry.ModItems
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
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
        val hudCategory = net.minecraft.client.KeyMapping.Category(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "hud"))
        val editHud = net.minecraft.client.KeyMapping("key.primalspells.hud_editor", org.lwjgl.glfw.GLFW.GLFW_KEY_H, hudCategory)
        bus.addListener<net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent> { event ->
            event.registerCategory(hudCategory); event.register(editHud)
        }
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory::class.java,
            java.util.function.Supplier { net.neoforged.neoforge.client.gui.IConfigScreenFactory { _, parent ->
                work.nekow.primalspells.client.screen.HudEditorScreen(parent)
            } })
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> {
            val mc = net.minecraft.client.Minecraft.getInstance()
            while (editHud.consumeClick()) if (mc.gui.screen() == null && mc.player != null)
                mc.gui.setScreen(work.nekow.primalspells.client.screen.HudEditorScreen())
        }
        // 1. 网络包的客户端处理器
        bus.addListener<net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent> { event ->
            event.register(work.nekow.primalspells.network.WandPreviewPayload.ID) { payload, _ -> ClientPayloadSink.wandPreview?.invoke(payload) }
        }
        ClientPayloadSink.wandPreview = { work.nekow.primalspells.client.input.WandEditorOverlay.accept(it); (net.minecraft.client.Minecraft.getInstance().gui.screen() as? work.nekow.primalspells.client.screen.WandPreviewScreen)?.accept(it) }
        val previewKey = net.minecraft.client.KeyMapping("key.primalspells.preview", org.lwjgl.glfw.GLFW.GLFW_KEY_V, hudCategory)
        bus.addListener<net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent> { it.register(previewKey) }
        fun openPreview() {
            val mc = net.minecraft.client.Minecraft.getInstance()
            val player = mc.player ?: return
            val screen = mc.gui.screen()
            if (screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*> && screen.hoveredSlot?.item?.item is work.nekow.primalspells.item.WandItem) {
                work.nekow.primalspells.client.input.WandEditorOverlay.open(screen, screen.hoveredSlot!!); return
            }
            val stack = if (screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) {
                val x = mc.mouseHandler.xpos() * mc.window.guiScaledWidth / mc.window.screenWidth
                val y = mc.mouseHandler.ypos() * mc.window.guiScaledHeight / mc.window.screenHeight
                screen.menu.slots.firstOrNull { it.isActive && x >= screen.getLeftPos() + it.x && x < screen.getLeftPos() + it.x + 18 && y >= screen.getTopPos() + it.y && y < screen.getTopPos() + it.y + 18 }?.item
                    ?: player.mainHandItem
            } else player.mainHandItem
            val id = stack.get(ModItems.WAND_ID)?.wandId ?: return
            mc.gui.setScreen(work.nekow.primalspells.client.screen.WandPreviewScreen(id, screen))
        }
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> {
            while (previewKey.consumeClick()) if (net.minecraft.client.Minecraft.getInstance().gui.screen() == null) openPreview()
        }
        NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre> {
            if (previewKey.matches(it.keyEvent) && it.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) { openPreview(); it.isCanceled = true }
        }
        ClientPayloadSink.hudImpact = { WandHudRenderer.impact(it.killed) }
        ClientPayloadSink.wandStats = { WandHudRenderer.putStats(it) }
        ClientPayloadSink.wandCast = { WandModelProperty.flash(it.wandId) }
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
                val config = work.nekow.particledrawing.config.ParticleDrawingConfig.CLIENT
                config.maxRenderParticles.set(200_000)
                config.particleBatchSize.set(65_536)
                ParticleManager.registerBuiltinTextures()
                ParticleTextures.register()
            }
        }

        // 3. 纯客户端事件与功能注册
        ScreenShakeClient.install()
        NeoForge.EVENT_BUS.register(work.nekow.primalspells.client.input.WandEditorOverlay)
        NeoForge.EVENT_BUS.register(ClientTooltipHandler)
        NeoForge.EVENT_BUS.register(ClientContainerDragHandler)
        NeoForge.EVENT_BUS.register(SpellSlotScrollHandler)

        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> {
            // 4. 清理只增不减的客户端缓存
            work.nekow.primalspells.client.input.WandEditorOverlay.clear()
            WandHudRenderer.clear()
            WandModelProperty.clear()
            ScreenShakeClient.clear()
            SpellSlotTracker.clear()
        }

        bus.addListener<RegisterRangeSelectItemModelPropertyEvent> { event ->
            event.register(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "wand_motion"), WandModelProperty.CODEC)
        }

        bus.addListener<RegisterClientExtensionsEvent> { event ->
            val wands = listOf(ModItems.WAND.get()) + ModItems.WAND_TIERS.values.map { it.get() }
            event.registerItem(WandHandPose, *wands.toTypedArray())
        }

        bus.addListener<RegisterClientTooltipComponentFactoriesEvent> { event ->
            event.register(WandSpellTooltip::class.java, ::ClientWandSpellTooltip)
            event.register(PouchTooltip::class.java, ::ClientPouchTooltip)
        }

        bus.addListener<EntityRenderersEvent.RegisterRenderers> { event ->
            event.registerEntityRenderer(ModEntities.ROGUE_MAGE.get(), ::RogueMageRenderer)
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
