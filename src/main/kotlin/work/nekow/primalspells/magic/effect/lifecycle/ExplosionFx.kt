package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ColorSource
import work.nekow.particledrawing.api.Draw
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleStyle
import work.nekow.particledrawing.api.ParticleVisual
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.ParticleShapes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 爆炸的**表现层**（只有爆发这一拍）。
 *
 * ⚠️ **第 32 轮按用户口径移除了蓄力/内爆**（「把爆炸的蓄力阶段给移除掉，直接爆」）：
 * 之前那套「黑核 + 三波丝线向心收束 + 向心粒子」的内爆表现、`chargeTicks` 参数与 `Deferred` 延迟结算
 * 全部删除，爆炸回到**触发即爆**。历史实现见 git 历史与 [docs/issues/resolved.md] 第 28~31 轮
 * （参考素材仍在 `.refs/explosion-vfx/`，但其中的"蓄力"部分**不再适用**）。
 *
 * 现在的观感（沿用第 30 轮定的「MC 粒子感」口径）：
 * **一大片小颗粒的球壳从 0.28R 撑到爆炸半径 + 三正交亮边 + 内部锯齿闪电丝 + 向外飞散的小颗粒
 * + 壳裂细弧 + 烟与贴地余晖**，全是小而多的粒子（世界尺寸是绝对档位，不随半径膨胀）。
 *
 * 尺寸换算（PD 1.0.13 口径）：`anisoWorld` 收**世界格数**；内置贴图可见尺寸小于标称
 * （SOFT_DOT ≈ 0.75×、LINE ≈ 0.7×），见 [dotWorld]/[lineWorld]。
 *
 * **0 包/tick**：只在爆发那一拍下发一次生成指令，其余（缩放/缓动/淡出/销毁）全在客户端本地求值。
 */
object ExplosionFx {

    // ---------------------------------------------------------------- 尺寸换算

    /** SOFT_DOT 可见直径 ≈ 0.75 × 标称尺寸 */
    private const val SOFT_DOT_VISIBLE = 0.75
    /** LINE 可见粗 ≈ 0.7 × 标称尺寸 */
    private const val LINE_VISIBLE = 0.7

    /** 让柔边点的**可见直径**为 [blocks] 格所需的 `anisoWorld` 值。 */
    private fun dotWorld(blocks: Double): Double = blocks / SOFT_DOT_VISIBLE

    /** 让线段的**可见粗**为 [blocks] 格所需的 `anisoWorld` 值。 */
    private fun lineWorld(blocks: Double): Double = blocks / LINE_VISIBLE

    /** 柔边点外观（可见直径 [blocks] 格，世界单位）。 */
    private fun dotVisual(blocks: Double, glow: Boolean = true): ParticleVisual {
        val size = dotWorld(blocks).toFloat()
        return ParticleVisual().style(ParticleStyle.SOFT_DOT)
            .anisoWorld(size, size)
            .glowing(glow)
            .additive(glow)
    }

    // ---------------------------------------------------------------- 绝对尺寸档位

    /** 火花（外飞粒子）：MC 粒子的量级 */
    private const val SPARK_DOT = 0.13

    /** 球壳点的可见直径：随半径**轻微**变化（大爆稍大），上限 0.42 —— "多而小" */
    private fun shellDot(radius: Float): Double = (radius * 0.055).coerceIn(0.14, 0.42)

    // ---------------------------------------------------------------- 爆发

    /** 壳起始半径（相对爆炸半径），随后 `scale` 撑到 1.0 */
    private const val SHELL_START_RATIO = 0.28f
    /** 壳的密铺间距（相对**可见直径**）：≤0.3 无空隙；这里略松，靠"多而小"读成粒子云 */
    private const val SHELL_SPACING = 0.42
    private const val SHELL_MAX_POINTS = 780
    private const val SHELL_GROW_TICKS = 8
    private const val SHELL_HOLD_TICKS = 3
    private const val SHELL_FADE_TICKS = 8
    private val SHELL_COLOR = Color.ofInt(74, 112, 142, 255)
    private val SHELL_RIM_COLOR = Color.ofInt(180, 235, 255, 255)

    /** 起爆闪光：几颗短命的大亮点（MC 的 FLASH 观感） */
    private const val FLASH_COUNT = 4
    private const val FLASH_LIFETIME = 4
    private val FLASH_COLOR = Color.ofInt(245, 253, 255, 255)

    private const val OUT_COUNT = 64
    private const val OUT_LIFETIME = 14
    private const val OUT_SPEED = 0.55
    private val OUT_COLOR = Color.ofInt(205, 240, 255, 255)

    private const val SMOKE_COUNT = 22
    private const val SMOKE_IN_TICKS = 3
    private const val SMOKE_FADE_TICKS = 8
    /** 烟消失时缩到原来的比例（逐渐变小消失） */
    private const val SMOKE_SHRINK = 0.35f
    private val SMOKE_COLOR = Color.ofInt(130, 136, 142, 255)

    // =====================================================================================
    // 爆发（唯一入口）
    // =====================================================================================

    /** 爆发：闪光 → 小颗粒球壳撑到爆炸半径 → 内部闪电丝 / 外飞 / 壳裂 / 烟与余晖。 */
    fun burst(manager: ParticleManager, center: Vec3, radius: Float) {
        if (radius <= 0f) return
        burstFlash(manager, center, radius)
        burstShell(manager, center, radius)
        burstOutward(manager, center, radius)
        burstAfterglow(manager, center, radius)
    }

    /** 起爆闪光：几颗短命亮点，峰值只维持 4 tick。 */
    private fun burstFlash(manager: ParticleManager, center: Vec3, radius: Float) {
        val size = dotWorld((radius * 0.30).coerceIn(0.5, 1.6))
        val visual = ParticleVisual().style(ParticleStyle.SOFT_DOT)
            .anisoWorld(size.toFloat(), size.toFloat()).glowing(true).additive(true)
        ParticleShapes.fibonacciSphere(FLASH_COUNT + 2).take(FLASH_COUNT).forEachIndexed { index, dir ->
            manager.create()
                .position(center.add(normalize(dir).scale(radius * 0.08 * index)))
                .color(FLASH_COLOR)
                .visual(visual.copy())
                .lifetime(FLASH_LIFETIME)
                .spawn()
        }
    }

    /** **球壳**：一大片小颗粒铺满球面，组级 `scale` 撑到爆炸半径（"多而小"才是 MC 的粒子云）。 */
    private fun burstShell(manager: ParticleManager, center: Vec3, radius: Float) {
        val dot = shellDot(radius)
        val shellStart = radius * SHELL_START_RATIO
        val points = shellPointCount(shellStart, dot)
        val shell = manager.createGroup(center)
        Draw.sphere(
            manager, center, shellStart.toDouble(), points,
            ColorSource.of(SHELL_COLOR), 1f, 0, shell, dotVisual(dot),
        )
        val rimVisual = dotVisual((dot * 1.6).coerceAtMost(0.6))
        for (axis in listOf(Draw.Axis.XZ, Draw.Axis.XY, Draw.Axis.YZ)) {
            Draw.circle(
                manager, center, shellStart.toDouble(), (points / 4).coerceIn(30, 180), axis,
                ColorSource.of(SHELL_RIM_COLOR), 1f, 0, shell, rimVisual,
            )
        }
        shell.fadeIn(1)
        shell.scale(1f / SHELL_START_RATIO, SHELL_GROW_TICKS, EasingType.EASE_OUT)
        shell.delay(SHELL_GROW_TICKS + SHELL_HOLD_TICKS)
        shell.fadeOut(SHELL_FADE_TICKS, removeAfter = true, easing = EasingType.EASE_OUT)
    }

    /** 球壳点数：按「间距 ≈ 0.42 × 可见直径」铺满球面并夹上限。 */
    private fun shellPointCount(shellRadius: Float, dot: Double): Int {
        val spacing = max(dot * SHELL_SPACING, 1.0e-3)
        val area = 4.0 * PI * shellRadius * shellRadius
        return (area / (spacing * spacing)).roundToInt().coerceIn(80, SHELL_MAX_POINTS)
    }

    /** 向外飞散的小颗粒：一次性初速，之后两端自行积分。 */
    private fun burstOutward(manager: ParticleManager, center: Vec3, radius: Float) {
        val visual = dotVisual(SPARK_DOT)
        ParticleShapes.fibonacciSphere(OUT_COUNT).forEach { dir ->
            val outward = normalize(dir)
            manager.create()
                .position(center.add(outward.scale(radius * 0.20)))
                .color(OUT_COLOR)
                .visual(visual.copy())
                .lifetime(OUT_LIFETIME)
                .spawn()
                ?.setVelocity(outward.scale(OUT_SPEED))
        }
    }

    /** 余韵：少量柔边"烟"上浮 + 贴地一圈小点。 */
    private fun burstAfterglow(manager: ParticleManager, center: Vec3, radius: Float) {
        // 烟云的轴心略高于爆心：`scale` 会把「到轴心的距离」和粒子尺寸一起缩，
        // 轴心放这里才读得出「烟自己在变小」，而不是被吸回爆心
        val smoke = manager.createGroup(center.add(0.0, radius * 0.22, 0.0))
        val smokeLife = SHELL_GROW_TICKS + SMOKE_IN_TICKS + SMOKE_FADE_TICKS + 2
        val smokeDot = (radius * 0.09).coerceIn(0.25, 0.7)
        ParticleShapes.fibonacciSphere(SMOKE_COUNT).forEachIndexed { index, dir ->
            val radial = normalize(dir)
            val p = center.add(radial.scale(radius * (0.15 + 0.25 * (index % 5) / 5.0)))
            val handle = manager.create()
                .position(p)
                .color(SMOKE_COLOR)
                .visual(dotVisual(smokeDot * (0.8 + 0.4 * (index % 3)), glow = false))
                .lifetime(smokeLife)
                .delay(SHELL_GROW_TICKS)
                .group(smoke.id)
                .spawn() ?: return@forEachIndexed
            handle.move(p.add(0.0, radius * 0.18, 0.0), 18, EasingType.EASE_OUT)
        }
        smoke.delay(SHELL_GROW_TICKS)
        smoke.fadeIn(SMOKE_IN_TICKS)
        // 逐渐变小消失：收缩与淡出同一窗口（EASE_IN = 越到后面缩得越快）
        smoke.scale(SMOKE_SHRINK, SMOKE_FADE_TICKS, EasingType.EASE_IN)
        smoke.fadeOut(SMOKE_FADE_TICKS, removeAfter = true, easing = EasingType.EASE_IN)

        val ground = manager.createGroup(center)
        Draw.circle(
            manager, center, radius * 0.55, 56, Draw.Axis.XZ,
            ColorSource.of(SHELL_COLOR), 1f, 0, ground, dotVisual(SPARK_DOT * 1.6),
        )
        ground.fadeIn(2)
        ground.fadeOut(SHELL_GROW_TICKS + SHELL_HOLD_TICKS + SHELL_FADE_TICKS, removeAfter = true)
    }

    // =====================================================================================
    // 小工具
    // =====================================================================================

    /** 单位化；长度过小时给一个稳定兜底（粒子生成不能因为除零整段失败）。 */
    private fun normalize(v: Vec3): Vec3 = if (v.lengthSqr() < 1e-9) Vec3(0.0, 1.0, 0.0) else v.normalize()

    /** 取一个与 [radial] 垂直的稳定切向（不随 tick 变化 —— 录制的可复现性优先于随机感）。 */
    private fun tangentOf(radial: Vec3): Vec3 {
        val up = if (abs(radial.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        return normalize(radial.cross(up))
    }
}
