package work.nekow.primalspells.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import work.nekow.primalspells.item.SpellPouchMenu

/** 法术小包容器界面（背景为直接纹理，取 176×166 区域） */
class SpellPouchScreen(
    menu: SpellPouchMenu,
    inventory: Inventory,
    title: Component,
) : AbstractContainerScreen<SpellPouchMenu>(menu, inventory, title) {

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)
        val yo = (height - imageHeight) / 2
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, yo, 0f, 0f, imageWidth, imageHeight, 256, 256)
    }

    companion object {
        val BACKGROUND: Identifier =
            Identifier.fromNamespaceAndPath("primalspells", "textures/gui/container/spell_pouch.png")
    }
}
