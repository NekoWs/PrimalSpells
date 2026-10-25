package work.nekow.primalspells.client.tooltip

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** 容器槽位右下角的剩余次数；与原版物品数量相同位置，0用红色区分耗尽卡。 */
internal object SpellUseTooltip {
    fun draw(graphics: GuiGraphicsExtractor, font: Font, uses: Int, x: Int, y: Int) {
        if (uses < 0) return
        val text = uses.toString()
        graphics.text(font, text, x + 17 - font.width(text), y + 10,
            if (uses == 0) 0xFFFF6979.toInt() else -1)
    }
}
