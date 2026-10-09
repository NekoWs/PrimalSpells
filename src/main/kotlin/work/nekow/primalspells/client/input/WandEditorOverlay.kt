package work.nekow.primalspells.client.input

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.network.*
import work.nekow.primalspells.wand.WandPreview

/** 原容器上的可拖动法杖悬浮编辑窗；保留背包交互，所有装卸由服务端验证。 */
object WandEditorOverlay {
    private var parent: AbstractContainerScreen<*>? = null
    private var targetSlot = -1
    private var id = ""
    var x = 8; private set
    var y = 8; private set
    private var scroll = 0
    private var drag = false
    private var grabX = 0; private var grabY = 0
    private var ticks = 0
    var preview: WandPreview? = null; private set
    private const val WIDTH = 232
    private const val HEIGHT = 146
    private fun sprite(name: String) = Identifier.fromNamespaceAndPath("primalspells", "wand_editor/$name")
    private fun slot(): Slot? = parent?.menu?.slots?.getOrNull(targetSlot)?.takeIf { it.item.get(ModItems.WAND_ID)?.wandId == id }
    /** 菜单内右键法杖或V调用；位置始终限制在屏幕中。 */
    fun open(screen: AbstractContainerScreen<*>, target: Slot) {
        val wandId = target.item.get(ModItems.WAND_ID)?.wandId ?: return
        parent = screen; targetSlot = target.index; id = wandId; preview = null; scroll = 0; ticks = 0
        x = (screen.getLeftPos() + 184).coerceIn(4, (screen.width - WIDTH - 4).coerceAtLeast(4))
        y = (screen.getTopPos() - 12).coerceIn(4, (screen.height - HEIGHT - 4).coerceAtLeast(4))
        ClientPacketDistributor.sendToServer(WandPreviewPayload(id))
    }
    /** 断线/关闭菜单清理，不保存服务端对象或跨世界缓存。 */
    fun clear() { parent = null; targetSlot = -1; id = ""; preview = null; drag = false }
    /** 仅接收当前目标的服务端解析结果。 */
    fun accept(payload: WandPreviewPayload) { if (payload.wandId == id) preview = payload.preview }
    /** 防止容器tooltip盖住悬浮编辑窗。 */
    fun contains(mx: Double, my: Double) = parent != null && mx >= x && mx < x + WIDTH && my >= y && my < y + HEIGHT
    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (parent == null) return
        if (Minecraft.getInstance().gui.screen() !== parent || slot() == null) { clear(); return }
        if (++ticks % 10 == 0) ClientPacketDistributor.sendToServer(WandPreviewPayload(id))
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun press(event: ScreenEvent.MouseButtonPressed.Pre) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        val mx = event.mouseX.toInt(); val my = event.mouseY.toInt()
        if (screen === parent && contains(event.mouseX, event.mouseY)) {
            event.isCanceled = true
            if (my < y + 18) {
                if (mx >= x + WIDTH - 18) clear() else { drag = true; grabX = mx - x; grabY = my - y }
                return
            }
            if (my in y + 63..y + 77) {
                Minecraft.getInstance().gui.setScreen(work.nekow.primalspells.client.screen.WandPreviewScreen(id, screen, preview))
                return
            }
            val col = (mx - x - 8) / 18; val row = (my - y - 23) / 18
            if (mx >= x + 8 && mx < x + 8 + 18 * 9 && my >= y + 23 && my < y + 59) {
                val selected = row * 9 + col
                val stack = slot()?.item ?: return
                val cards = stack.get(ModItems.WAND_SPELLS).orEmpty()
                if (selected in cards.indices && (event.button == 0 || event.button == 1)) {
                    val player = Minecraft.getInstance().player ?: return
                    val menu = if (screen is net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) player.inventoryMenu else screen.menu
                    val target = slot() ?: return
                    val serverSlot = if (menu === screen.menu) target else menu.slots.firstOrNull {
                        it.container === target.container && it.slotIndex == target.slotIndex
                    } ?: return
                    val cursor = screen.menu.carried
                    ClientPacketDistributor.sendToServer(EditWandPayload(menu.containerId, serverSlot.index, id, selected,
                        if (player.isCreative && !cursor.isEmpty) ModItems.spellIdOf(cursor.item) ?: "invalid" else "",
                        if (player.isCreative) cursor.count else -1))
                }
            }
            return
        }
        val target = screen.hoveredSlot ?: return
        if (event.button == 1 && target.item.item is WandItem && screen.menu.carried.isEmpty) {
            open(screen, target); event.isCanceled = true
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun release(event: ScreenEvent.MouseButtonReleased.Pre) { if (drag) { drag = false; event.isCanceled = true } }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun move(event: ScreenEvent.MouseDragged.Pre) {
        if (!drag || event.screen !== parent) return
        val screen = parent ?: return
        x = (event.mouseX.toInt() - grabX).coerceIn(4, (screen.width - WIDTH - 4).coerceAtLeast(4))
        y = (event.mouseY.toInt() - grabY).coerceIn(4, (screen.height - HEIGHT - 4).coerceAtLeast(4))
        event.isCanceled = true
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun wheel(event: ScreenEvent.MouseScrolled.Pre) {
        if (event.screen === parent && contains(event.mouseX, event.mouseY)) { scroll = (scroll - event.scrollDeltaY.toInt() * 2).coerceAtLeast(0); event.isCanceled = true }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun key(event: ScreenEvent.KeyPressed.Pre) {
        if (event.screen === parent && event.keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { clear(); event.isCanceled = true }
    }
    @SubscribeEvent
    fun close(event: ScreenEvent.Closing) { if (event.screen === parent) clear() }
    @SubscribeEvent
    fun render(event: ScreenEvent.Render.Post) {
        if (event.screen !== parent) return
        val stack = slot()?.item ?: return
        val g = event.guiGraphics; val font = Minecraft.getInstance().font
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("panel"), x, y, WIDTH, HEIGHT)
        g.text(font, stack.hoverName, x + 8, y + 7, 0xFF383838.toInt(), false)
        g.text(font, "×", x + WIDTH - 13, y + 7, 0xFF383838.toInt(), false)
        val cards = stack.get(ModItems.WAND_SPELLS).orEmpty()
        for (i in cards.indices.take(18)) {
            val sx = x + 8 + i % 9 * 18; val sy = y + 23 + i / 9 * 18
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("slot"), sx, sy, 18, 18)
            if (event.mouseX in sx until sx + 18 && event.mouseY in sy until sy + 18)
                g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("selected"), sx, sy, 18, 18)
            ModItems.spellItem(cards[i])?.let { g.item(ItemStack(it), sx + 1, sy + 1) }
        }
        val p = preview
        val summary = if (p == null) Component.translatable("preview.primalspells.wait") else
            Component.translatable("preview.primalspells.summary", "%.0f".format(p.manaAfter), p.delay.toInt(), p.recharge.toInt(), p.shots)
        g.text(font, summary, x + 8, y + 65, 0xFF383838.toInt(), false)
        g.text(font, "↗", x + WIDTH - 16, y + 65, 0xFF553388.toInt(), false)
        val lines = p?.rows.orEmpty()
        scroll = scroll.coerceIn(0, (lines.size - 4).coerceAtLeast(0))
        g.enableScissor(x + 8, y + 78, x + WIDTH - 8, y + 127)
        lines.drop(scroll).take(4).forEachIndexed { i, row ->
            val name = Component.translatable("item.primalspells.${row.id}").string
            val scope = if (row.scope == "root") "◇" else "↳"
            val text = "${"  ".repeat(row.depth.coerceAtMost(8))}${if (row.accepted) scope else "×"} $name · ${row.mana}MP"
            g.text(font, text, x + 8, y + 79 + i * 12, if (!row.accepted) 0xFFAD3030.toInt() else if (row.scope == "root") 0xFF553388.toInt() else 0xFF246E75.toInt(), false)
        }
        g.disableScissor()
        g.text(font, Component.translatable("editor.primalspells.help"), x + 8, y + 132, 0xFF555555.toInt(), false)
        if (contains(event.mouseX.toDouble(), event.mouseY.toDouble()) && !parent!!.menu.carried.isEmpty) {
            g.item(parent!!.menu.carried, event.mouseX - 8, event.mouseY - 8)
            g.itemDecorations(font, parent!!.menu.carried, event.mouseX - 8, event.mouseY - 8)
        }
    }
}
