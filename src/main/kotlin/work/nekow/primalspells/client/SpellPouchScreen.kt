package work.nekow.primalspells.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
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

    override fun renderSlotContents(
        graphics: GuiGraphicsExtractor,
        itemStack: ItemStack,
        slot: Slot,
        itemCount: String?,
    ) {
        // 26.2 物品图标默认渲染在 (slot.x, slot.y) 起（16x16 不居中）；
        // +1 使其居中，与贴图内凹区（1px 描边内的 16x16）对齐。
        val x = slot.x + 1
        val y = slot.y + 1
        val seed = slot.x + slot.y * imageWidth
        if (slot.isFake) {
            graphics.fakeItem(itemStack, x, y, seed)
        } else {
            graphics.item(itemStack, x, y, seed)
        }
        val font = IClientItemExtensions.of(itemStack).getFont(itemStack, IClientItemExtensions.FontContext.ITEM_COUNT)
        graphics.itemDecorations(font ?: this.font, itemStack, x, y, itemCount)
    }

    companion object {
        val BACKGROUND: Identifier =
            Identifier.fromNamespaceAndPath("primalspells", "textures/gui/container/spell_pouch.png")
    }
}
