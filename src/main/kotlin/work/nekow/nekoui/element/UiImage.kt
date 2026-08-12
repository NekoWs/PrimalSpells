package work.nekow.nekoui.element

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import work.nekow.nekoui.UiScreen

/**
 * 图片：在指定区域渲染一张资源包贴图（全图拉伸铺满）。
 * 贴图必须位于 `assets/<namespace>/textures/...` 下，例如 `primalspells:textures/item/wand.png`。
 */
class UiImage(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val texture: Identifier,
    val color: Int = -1,
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        if (color == -1) {
            graphics.blit(texture, x, y, x + width, y + height, 0f, 1f, 0f, 1f)
        } else {
            graphics.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                texture, x, y, 0f, 0f, width, height, width, height, 16, 16, color
            )
        }
    }
}
