package work.nekow.primalspells.client.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.*

/** 一帧的只读HUD数值；没有“充能中”等文案，全部由图标、数值、进度表达。 */
data class WandHudFrame(
    val mana: Double, val maxMana: Double, val delay: Double = 0.0, val recharge: Double = 0.0,
    val delayProgress: Double = 1.0, val rechargeProgress: Double = 1.0,
    val manaBlocked: Boolean = false, val presence: Double = 1.0,
    val delayAlpha: Double = 0.0, val rechargeAlpha: Double = 0.0,
    val impacts: List<HudImpactFrame> = emptyList(),
    val hurtAngle: Double? = null, val hurtAlpha: Double = 0.0,
)

/** 硬边像素镶框、晶石和沙漏；正式HUD与拖动预览复用同一绘制函数。 */
object WandHudPainter {
    const val CYAN = 0xFF78E5F2.toInt()
    const val PURPLE = 0xFFD29AFF.toInt()
    private const val WHITE = -0x100001
    private const val BORDER = 0xFF8D91AF.toInt()
    private const val DARK = 0xFF151A2B.toInt()

    /** 按同一边界矩阵缩放全部图标和文字，选框不会与实际HUD脱节。 */
    fun drawElement(g: GuiGraphicsExtractor, element: HudElement, box: HudBox, frame: WandHudFrame, time: Double) {
        g.pose().pushMatrix()
        g.pose().translate(box.x.toFloat(), box.y.toFloat()).scale(box.scale.toFloat())
        when (element) {
            HudElement.MANA -> mana(g, frame, time)
            HudElement.COOLDOWN -> cooldown(g, frame)
            HudElement.FOCUS -> feedback(g, frame, box.scale)
        }
        g.pose().popMatrix()
    }

    private fun tint(color: Int, alpha: Double): Int = ((255 * alpha.coerceIn(0.0, 1.0)).roundToInt() shl 24) or (color and 0xFFFFFF)
    private fun rect(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, c: Int, a: Double = 1.0) {
        if (a > 0.003 && w > 0 && h > 0) g.fill(x, y, x + w, y + h, tint(c, a))
    }
    private fun number(g: GuiGraphicsExtractor, value: String, x: Int, y: Int, c: Int, a: Double) {
        if (a > 0.003) g.text(Minecraft.getInstance().font, value, x, y, tint(c, a), true)
    }
    private fun diamond(g: GuiGraphicsExtractor, x: Int, y: Int, radius: Int, c: Int, a: Double, filled: Boolean = false) {
        for (i in -radius..radius) {
            val dx = radius - abs(i)
            if (filled) rect(g, x - dx, y + i, dx * 2 + 1, 1, c, a)
            else { rect(g, x - dx, y + i, 1, 1, c, a); rect(g, x + dx, y + i, 1, 1, c, a) }
        }
    }
    private fun crystal(g: GuiGraphicsExtractor, x: Int, y: Int, c: Int, a: Double, time: Double) {
        diamond(g, x, y, 7, DARK, a, true); diamond(g, x, y, 6, BORDER, a)
        diamond(g, x, y, 4, c, a, true); diamond(g, x - 1, y - 1, 2, WHITE, a, true)
        rect(g, x - 1, y - 11, 2, 3, c, a); rect(g, x - 1, y + 9, 2, 3, c, a)
        val step = floor(time / 3).toInt() and 3
        listOf(-9 to 0, 0 to -9, 9 to 0, 0 to 9).forEachIndexed { i, (dx, dy) ->
            rect(g, x + dx, y + dy, 1, 1, if (i == step) WHITE else c, a * if (i == step) 1.0 else 0.6)
        }
    }
    private fun bar(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, ratio: Double, c: Int, a: Double, segments: Int = 0) {
        rect(g, x, y, width, 8, 0xFF080C16.toInt(), a)
        rect(g, x + 1, y, width - 2, 1, BORDER, a); rect(g, x + 1, y + 7, width - 2, 1, BORDER, a)
        rect(g, x, y + 1, 1, 6, BORDER, a); rect(g, x + width - 1, y + 1, 1, 6, BORDER, a)
        rect(g, x + 2, y + 2, width - 4, 4, DARK, a)
        val fill = ((width - 4) * ratio.coerceIn(0.0, 1.0)).roundToInt()
        rect(g, x + 2, y + 2, fill, 4, c, a)
        rect(g, x + 2, y + 2, fill, 1, WHITE, a * 0.6)
        if (fill > 0) rect(g, x + 1 + fill, y + 2, 1, 4, WHITE, a)
        if (segments > 0) for (i in 1 until segments) rect(g, x + 2 + (width - 4) * i / segments, y + 2, 1, 4, DARK, a)
    }
    private fun mana(g: GuiGraphicsExtractor, f: WandHudFrame, t: Double) {
        val ratio = if (f.maxMana > 0) f.mana / f.maxMana else 0.0
        val low = f.manaBlocked || ratio < 0.15
        val c = if (low) 0xFFFF6979.toInt() else CYAN
        val a = f.presence
        crystal(g, 9, 13, c, a, t)
        number(g, "${floor(f.mana.coerceAtLeast(0.0)).toInt()}/${ceil(f.maxMana).toInt()}", 25, 0, c, a)
        bar(g, 24, 12, 100, ratio, c, a)
        if (low) {
            // 明确的不足符号，不用闪烁整条HUD；低法力仍保留读数。
            rect(g, 116, 0, 7, 7, DARK, a)
            rect(g, 119, 1, 1, 3, c, a); rect(g, 119, 5, 1, 1, c, a)
        }
    }
    private fun hourglass(g: GuiGraphicsExtractor, y: Int, c: Int, a: Double) {
        rect(g, 3, y, 9, 1, BORDER, a); rect(g, 3, y + 10, 9, 1, BORDER, a)
        for (i in 0..3) { rect(g, 4 + i, y + 1 + i, 1, 1, c, a); rect(g, 10 - i, y + 1 + i, 1, 1, c, a)
            rect(g, 7 - i, y + 6 + i, 1 + i * 2, 1, c, a) }
    }
    private fun cooldown(g: GuiGraphicsExtractor, f: WandHudFrame) {
        val da = f.delayAlpha * f.presence; val ra = f.rechargeAlpha * f.presence
        hourglass(g, 1, WHITE, da); bar(g, 18, 3, 54, f.delayProgress, WHITE, da, 6)
        number(g, ceil(f.delay).toInt().toString(), 77, 3, WHITE, da)
        diamond(g, 7, 21, 5, PURPLE, ra); diamond(g, 7, 21, 2, PURPLE, ra, true)
        bar(g, 18, 17, 54, f.rechargeProgress, PURPLE, ra, 6)
        number(g, ceil(f.recharge).toInt().toString(), 77, 17, PURPLE, ra)
    }
    private fun feedback(g: GuiGraphicsExtractor, f: WandHudFrame, scale: Double) {
        val cx = 45; val cy = 29
        for (impact in f.impacts) {
            impactMarker(g, impact, cx, cy, scale, f.presence)
        }
        // 受击弧位于外圈；折线缺口与晶齿保留像素魔法感。
        val hurt = f.hurtAlpha * f.presence
        if (hurt > 0.003) {
            val angles = f.hurtAngle?.let { listOf(it) } ?: listOf(-PI / 2, 0.0, PI / 2, PI)
            for (angle in angles) {
                for (i in -7..7) {
                    if (i == -3 || i == 3) continue
                    val a = angle + i * 0.047
                    val radius = 24 + 2 * (1 - f.hurtAlpha)
                    val x = cx + (cos(a) * radius).roundToInt()
                    val y = cy + (sin(a) * radius).roundToInt()
                    rect(g, x, y, 1, 1, 0xFFFF4B64.toInt(), hurt)
                }
                val x = cx + (cos(angle) * 22).roundToInt(); val y = cy + (sin(angle) * 22).roundToInt()
                diamond(g, x, y, 1, 0xFFFFA5B4.toInt(), hurt)
            }
        }
    }

    /** 正十字命中、斜十字击杀；GUI矩阵保留小数位，硬边短线持续移动，不逐格停顿。 */
    private fun impactMarker(g: GuiGraphicsExtractor, impact: HudImpactFrame, cx: Int, cy: Int, scale: Double, presence: Double) {
        // 命中接续途中可能马上击杀；先画完整的有限尾段，不能丢掉上一层正在淡出的笔画。
        impact.previous?.let { impactMarker(g, it, cx, cy, scale, presence) }
        val alpha = impact.alpha * presence
        if (alpha <= 0.003) return
        val core = if (impact.killed) 0xFFFFB1BA.toInt() else WHITE
        val color = if (impact.killed) 0xFFFF4B64.toInt() else 0xFFE0CFFF.toInt()
        for (stroke in impact.strokes(scale)) {
            g.pose().pushMatrix()
            g.pose().translate((cx + stroke.x).toFloat(), (cy + stroke.y).toFloat())
                .scale(stroke.width.toFloat(), stroke.height.toFloat())
            g.fill(0, 0, 1, 1, tint(if (stroke.core) core else color, alpha))
            g.pose().popMatrix()
        }
    }
}
