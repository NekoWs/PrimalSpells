package work.nekow.primalspells.ui.element

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.ui.UiScreen

/**
 * 物品槽位：槽位背景 + 可选物品图标（16×16 居中）。
 * 宽高默认 18×18（原版槽位大小）。
 *
 * 渲染分两层：[drawBackground]（背景/边框/悬停高亮）与 [drawItem]（物品图标），
 * 供拖动控制器组合渲染（拾起隐藏物品、动画期间只画背景+飞行物品）。
 *
 * 数据约定：槽位 [stack] 永远来自服务端同步的权威内容（见 SpellSlotGrid.refresh），
 * 拖动过程不修改它；拾起状态仅通过 [picked] 标志在渲染层隐藏物品。
 */
class UiSlot(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    var stack: ItemStack = ItemStack.EMPTY,
    val showBackground: Boolean = true,
    val hoverHighlight: Boolean = true,
    val borderColor: Int = 0xFF_4E_4E_5A.toInt(),
    val hoverBorderColor: Int = 0xFF_8F_C9_FF.toInt(),
) : UiElement(x, y, width, height) {

    var onClick: ((UiScreen?, Int) -> Boolean)? = null

    /** 被拖动控制器拾起中：渲染时隐藏物品图标（跟随鼠标的物品由控制器绘制） */
    var picked: Boolean = false

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        drawBackground(graphics, mouseX, mouseY)
        if (!picked) drawItem(graphics, fontOf(screen))
    }

    /** 仅绘制槽位背景 / 边框 / 悬停高亮（供动画与拾起态组合渲染） */
    fun drawBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!visible) return
        if (showBackground) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND, x, y, width, height)
        } else {
            graphics.fill(x, y, x + width, y + height, 0xFF_1A_1A_1E.toInt())
        }
        // 描边
        val hovered = contains(mouseX.toDouble(), mouseY.toDouble())
        val border = if (hovered && hoverHighlight) hoverBorderColor else borderColor
        graphics.fill(x, y, x + width, y + 1, border)
        graphics.fill(x, y, x + 1, y + height, border)
        graphics.fill(x + width - 1, y, x + width, y + height, border)
        graphics.fill(x, y + height - 1, x + width, y + height, border)
        if (hoverHighlight && hovered) {
            graphics.fill(x, y, x + width, y + height, 0x40_FF_FF_FF.toInt())
        }
    }

    /** 仅绘制物品图标（16×16 居中） */
    fun drawItem(graphics: GuiGraphicsExtractor, font: Font) {
        if (!stack.isEmpty) {
            graphics.item(stack, x + (width - 16) / 2, y + (height - 16) / 2)
        }
    }

    override fun mouseClicked(screen: UiScreen?, button: Int, mx: Double, my: Double): Boolean {
        if (!visible || !contains(mx, my)) return false
        return onClick?.invoke(screen, button) ?: false
    }

    companion object {
        val SLOT_BACKGROUND: Identifier = Identifier.withDefaultNamespace("container/bundle/slot_background")
    }
}
