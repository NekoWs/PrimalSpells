package work.nekow.primalspells.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import work.nekow.primalspells.client.hud.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** HUD布局编辑：实际绘制函数预览、拖动、滚轮/角柄缩放，保存/取消有独立草稿。 */
class HudEditorScreen(private val parent: Screen? = null) : Screen(Component.translatable("screen.primalspells.hud.title")) {
    var draft = HudSettings.current.duplicate()
        private set
    var selected = HudElement.MANA
        private set
    private var drag = false
    private var resizing = false
    private var grabX = 0.0
    private var grabY = 0.0
    private var startScale = 1.0
    private var startMouseX = 0.0
    private var saveFailed = false

    override fun init() {
        val buttonWidth = (width / 5).coerceIn(50, 110)
        addRenderableWidget(Button.builder(Component.translatable("screen.primalspells.hud.reset")) { draft = HudLayout() }
            .bounds(width / 2 - buttonWidth * 3 / 2 - 6, height - 25, buttonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel")) { onClose() }
            .bounds(width / 2 - buttonWidth / 2, height - 25, buttonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("screen.primalspells.hud.save")) {
            saveFailed = !HudSettings.save(draft)
            if (!saveFailed) minecraft.gui.setScreen(parent)
        }.bounds(width / 2 + buttonWidth / 2 + 6, height - 25, buttonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("screen.primalspells.hud.hits")) {
            draft.hitFeedback = !draft.hitFeedback
        }.bounds(8, 36, 100, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("screen.primalspells.hud.direction")) {
            draft.damageDirection = !draft.damageDirection
        }.bounds(width - 108, 36, 100, 20).build())
    }
    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(0, 0, width, height, 0x55101624)
    }
    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.text(font, title, width / 2 - font.width(title) / 2, 8, -1)
        val help = Component.translatable("screen.primalspells.hud.help")
        g.text(font, help, width / 2 - font.width(help) / 2, 22, 0xFFC1B6DC.toInt())
        val preview = WandHudFrame(68.0, 100.0, 7.0, 18.0, 0.55, 0.4,
            delayAlpha = 1.0, rechargeAlpha = 1.0,
            impacts = if (draft.hitFeedback) listOf(HudImpactFrame(0, false, 0, 0.8, 1.0, 0.0)) else emptyList(),
            hurtAngle = if (draft.damageDirection) 0.0 else null, hurtAlpha = 0.8)
        for (element in HudElement.entries) {
            val box = draft.box(element, width, height)
            WandHudPainter.drawElement(g, element, box, preview, (minecraft.level?.gameTime ?: 0) + partialTick.toDouble())
            val color = if (element == selected) WandHudPainter.PURPLE else 0xFF647087.toInt()
            outline(g, box, color)
            if (element == selected) {
                g.fill((box.x + box.width - 3).roundToInt(), (box.y + box.height - 3).roundToInt(),
                    (box.x + box.width + 3).roundToInt(), (box.y + box.height + 3).roundToInt(), color)
            }
        }
        val selectedText = Component.translatable("screen.primalspells.hud.selected", Component.translatable(selected.labelKey),
            (draft.placement(selected).scale * 100).roundToInt())
        g.text(font, selectedText, width / 2 - font.width(selectedText) / 2, height - 39, WandHudPainter.PURPLE)
        g.text(font, if (draft.hitFeedback) "✓" else "—", 112, 42, WandHudPainter.CYAN)
        g.text(font, if (draft.damageDirection) "✓" else "—", width - 120, 42, WandHudPainter.CYAN)
        if (saveFailed) g.text(font, Component.translatable("screen.primalspells.hud.save_failed"), 8, 62, 0xFFFF6979.toInt())
        super.extractRenderState(g, mouseX, mouseY, partialTick)
    }
    private fun outline(g: GuiGraphicsExtractor, b: HudBox, color: Int) {
        val x = b.x.roundToInt() - 2; val y = b.y.roundToInt() - 2
        val r = (b.x + b.width).roundToInt() + 2; val bottom = (b.y + b.height).roundToInt() + 2
        g.fill(x, y, r, y + 1, color); g.fill(x, bottom, r, bottom + 1, color)
        g.fill(x, y, x + 1, bottom, color); g.fill(r, y, r + 1, bottom, color)
    }
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() != 0) return false
        val chosen = draft.box(selected, width, height)
        if (abs(event.x() - chosen.x - chosen.width) <= 6 && abs(event.y() - chosen.y - chosen.height) <= 6) {
            resizing = true; startScale = draft.placement(selected).scale; startMouseX = event.x(); return true
        }
        val hit = HudElement.entries.reversed().firstOrNull { draft.box(it, width, height).contains(event.x(), event.y()) } ?: return false
        selected = hit
        val b = draft.box(hit, width, height)
        grabX = event.x() - b.x - b.width / 2; grabY = event.y() - b.y - b.height / 2
        drag = true
        return true
    }
    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (event.button() != 0) return false
        if (resizing) {
            draft.placement(selected).scale = (startScale + (event.x() - startMouseX) / selected.width).coerceIn(0.6, 2.5)
            return true
        }
        if (!drag) return super.mouseDragged(event, dx, dy)
        fun snap(v: Double, target: Double) = if (abs(v - target) < 4) target else v
        draft.move(selected, snap(event.x() - grabX, width / 2.0), snap(event.y() - grabY, height / 2.0), width, height)
        return true
    }
    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0 && (drag || resizing)) { drag = false; resizing = false; return true }
        return super.mouseReleased(event)
    }
    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val element = HudElement.entries.reversed().firstOrNull { draft.box(it, width, height).contains(x, y) }
        if (element != null) selected = element
        draft.placement(selected).scale = (draft.placement(selected).scale + scrollY * 0.05).coerceIn(0.6, 2.5)
        return true
    }
    override fun keyPressed(event: KeyEvent): Boolean {
        val amount = if (event.hasShiftDown()) 10 else 1
        val delta = when (event.key()) { GLFW.GLFW_KEY_LEFT -> -amount to 0; GLFW.GLFW_KEY_RIGHT -> amount to 0
            GLFW.GLFW_KEY_UP -> 0 to -amount; GLFW.GLFW_KEY_DOWN -> 0 to amount; else -> null }
        if (delta != null) {
            val b = draft.box(selected, width, height)
            draft.move(selected, b.x + b.width / 2 + delta.first, b.y + b.height / 2 + delta.second, width, height); return true
        }
        return super.keyPressed(event)
    }
    override fun onClose() { minecraft.gui.setScreen(parent) }
}
