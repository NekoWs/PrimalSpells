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
 * 爆炸的**表现层**：内爆蓄力（[implode]）→ 爆发（[burst]）。
 *
 * ## 口径（用户第 30 轮定的，比参考图优先）
 *
 * > 「整体的贴图感太重，特别是蓄力阶段。蓄力的线条和粒子：是每个有自己的动画出现 → 逐渐往中间汇聚，
 * > 而不是简单的整体缩小一些而已。不是完美复刻我给的参考，而是用 **MC 本身的粒子感**还原一个符合 MC 风格的版本。」
 *
 * 落实成三条硬约束：
 *
 * 1. **不做"整体缩小"**：蓄力**分波出现** —— 每波丝线有自己的组、自己的延迟与淡入（`stagger` 让丝线**逐段扫出**），
 *    向心粒子**各自带速度**飞向中心；没有任何"整组 scale 一缩了事"。
 * 2. **粒子的世界尺寸是绝对档位，不随爆炸半径膨胀**（MC 粒子的观感前提）：
 *    [shellDot]/[coreDot]/[sparkDot] 只做很小的半径相关夹取，绝大多数情况是固定的 0.1~0.4 格 —— 小而多，而不是大而少。
 * 3. **不用大柔边块、不用半透明大膜**：球壳是**一大片小颗粒**铺出来的；亮边是细环；丝线是细线（可见粗 ≈ 0.06 格）。
 *
 * 尺寸换算（PD 1.0.13 口径）见 [dotWorld]/[lineWorld]：`anisoWorld` 收**世界格数**；
 * 内置贴图**可见尺寸小于标称**（SOFT_DOT ≈ 0.75×、LINE ≈ 0.7×）。
 *
 * **0 包/tick**：只在下发与爆发两拍各下发一次生成指令，其余（延迟/速度/缓动/淡出/销毁）全在客户端本地求值。
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

    // ---------------------------------------------------------------- 绝对尺寸档位（不随半径膨胀）

    /** 火花（向心粒子 / 外飞粒子）：MC 粒子的量级 */
    private const val SPARK_DOT = 0.13
    /** 蓄力丝线的可见粗：细线，不是带子 */
    private const val FILAMENT_WIDTH = 0.14
    /** 内部闪电丝的可见粗 */
    private const val BOLT_WIDTH = 0.05
    /** 壳裂弧段的可见粗 */
    private const val CRACK_WIDTH = 0.045
    /** 黑核表面点的可见直径 */
    private const val CORE_DOT = 0.30
    /** 黑核亮边的可见直径 */
    private const val RIM_DOT = 0.12

    /** 球壳点的可见直径：随半径**轻微**变化（大爆稍微大一点），上限 0.42 —— 保证"多而小" */
    private fun shellDot(radius: Float): Double = (radius * 0.055).coerceIn(0.14, 0.42)

    // ---------------------------------------------------------------- 蓄力：黑核

    /** 核心球半径 = 爆炸半径 × 1/3（用户口径） */
    private const val CORE_RATIO = 1f / 3f
    /** 核表面点数（用密集小点铺球面 → 暗球，而不是一颗大方块） */
    private const val CORE_POINTS = 130
    /** 核亮边：三正交平面各一圈小点 */
    private const val CORE_RING_COUNT = 56
    private val CORE_COLOR = Color.ofInt(6, 8, 16, 255)
    private val RIM_COLOR = Color.ofInt(180, 244, 255, 255)

    // ---------------------------------------------------------------- 蓄力：丝线（分波）

    /** 分几波收拢（每波一组，各自的延迟/淡入/半径/相位） */
    private const val WAVE_COUNT = 3
    /** 每波的丝线根数 */
    private const val FILAMENT_PER_WAVE = 8
    /** 每条丝线的折线点数（**每段一颗粒子**） */
    private const val FILAMENT_SEGMENTS = 12
    /** 每波起点半径（相对爆炸半径）：外层 → 内层 */
    private val WAVE_START = doubleArrayOf(0.95, 0.66, 0.42)
    /** 相邻两波之间隔多少 tick 出现（"每个有自己的动画出现"的节拍） */
    private const val WAVE_GAP = 4
    /** 每波淡入时长 */
    private const val WAVE_FADE_IN = 2
    /** 收束：把整波缩到中心的比例（0 = 完全收进核心）与时长 */
    private const val RUSH_SCALE = 0.05f
    private const val RUSH_TICKS = 4
    /** 相邻波的收束错开（外波先吸） */
    private const val RUSH_STAGGER = 2
    /** 线段两端外扩比例：抵消 LINE 贴图两端各 ≈0.2 的渐隐，避免虚线 */
    private const val SEGMENT_OVERLAP = 0.22
    /** 丝线绕核的螺旋圈数 */
    private const val FILAMENT_TWIST = 0.5
    private const val FILAMENT_SPIRAL = 0.33
    /** 每波相位偏移（避免三波叠成同一张图） */
    private const val WAVE_PHASE = 0.7
    private val FILAMENT_INNER = Color.ofInt(232, 252, 255, 255)
    private val FILAMENT_OUTER = Color.ofInt(96, 200, 255, 255)

    // ---------------------------------------------------------------- 蓄力：向心粒子

    private const val CONVERGE_COUNT = 48
    private const val CONVERGE_DELAY_STEPS = 7
    /** 向心速度系数：让粒子大致在蓄力结束时抵达核心（各自速度，不是组位移） */
    private const val CONVERGE_SPEED = 1.05
    private val CONVERGE_COLOR = Color.ofInt(160, 230, 255, 255)

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

    private const val BOLT_COUNT = 10
    private const val BOLT_SEGMENTS = 10
    private val BOLT_COLOR = Color.ofInt(225, 250, 255, 255)

    private const val OUT_COUNT = 64
    private const val OUT_LIFETIME = 14
    private const val OUT_SPEED = 0.55
    private val OUT_COLOR = Color.ofInt(205, 240, 255, 255)

    private const val CRACK_COUNT = 6
    private const val CRACK_SEGMENTS = 6

    private const val SMOKE_COUNT = 22
    private const val SMOKE_IN_TICKS = 3
    private const val SMOKE_FADE_TICKS = 8
    private val SMOKE_COLOR = Color.ofInt(130, 136, 142, 255)

    /**
     * 由半径反推蓄力时长（tick）：`半径 × 3`，夹在 4~20 tick。
     *
     * `small_explosion`(半径 3) 与 `large_explosion`(半径 6) 在各自法术里**显式**指定 8 / 18 tick
     * （用户口径 0.4 s / 0.9 s），这里只服务其余爆炸（如火球命中，半径 2 → 6 tick）。
     */
    fun autoChargeTicks(radius: Float): Int = (radius * 3f).roundToInt().coerceIn(4, 20)

    // =====================================================================================
    // 蓄力
    // =====================================================================================

    /**
     * 蓄力表现：黑核常驻 → 三波丝线**依次出现**并向心收拢 → 各自带速度的向心粒子。
     *
     * 所有销毁都在客户端本地排好（`delay` + `fadeOut(removeAfter = true)`），服务端不再发任何指令。
     */
    fun implode(manager: ParticleManager, center: Vec3, radius: Float, chargeTicks: Int) {
        if (radius <= 0f || chargeTicks <= 0) return
        val coreRadius = radius * CORE_RATIO
        implodeCore(manager, center, coreRadius, chargeTicks)
        implodeWaves(manager, center, coreRadius, radius, chargeTicks)
        implodeConverge(manager, center, coreRadius, radius, chargeTicks)
    }

    /** **黑核**：密集小点铺出的暗球 + 三正交圈的小亮点亮边。 */
    private fun implodeCore(manager: ParticleManager, center: Vec3, coreRadius: Float, chargeTicks: Int) {
        val group = manager.createGroup(center)
        Draw.sphere(
            manager, center, coreRadius.toDouble(), CORE_POINTS,
            ColorSource.of(CORE_COLOR), 1f, 0, group, dotVisual(CORE_DOT, glow = false),
        )
        val rim = dotVisual(RIM_DOT)
        for (axis in listOf(Draw.Axis.XZ, Draw.Axis.XY, Draw.Axis.YZ)) {
            Draw.circle(
                manager, center, coreRadius.toDouble(), CORE_RING_COUNT, axis,
                ColorSource.of(RIM_COLOR), 1f, 0, group, rim,
            )
        }
        group.fadeIn(2)
        group.delay((chargeTicks - 4).coerceAtLeast(0))
        group.fadeOut(4, removeAfter = true, easing = EasingType.EASE_IN)
    }

    /**
     * **三波丝线**：每波一个组、各自的延迟与淡入淡出，起点半径逐波收里 ——
     * 于是观感是「一批丝线扫出来 → 收拢 → 下一批更靠里地扫出来」，而不是整张图一起缩小。
     */
    private fun implodeWaves(
        manager: ParticleManager,
        center: Vec3,
        coreRadius: Float,
        radius: Float,
        chargeTicks: Int,
    ) {
        val nominalWidth = lineWorld(FILAMENT_WIDTH).toFloat()
        val gradient = ColorSource { t -> FILAMENT_OUTER.lerp(FILAMENT_INNER, t.toFloat()) }
        val dirs = ParticleShapes.fibonacciSphere(FILAMENT_PER_WAVE)

        for (wave in 0 until WAVE_COUNT) {
            val group = manager.createGroup(center)
            val startRatio = WAVE_START[wave]
            val endRatio = if (wave == WAVE_COUNT - 1) 1.2 else WAVE_START.getOrElse(wave + 1) { 0.4 } * CORE_RATIO / CORE_RATIO * 0.85
            for (index in 0 until FILAMENT_PER_WAVE) {
                val axis = normalize(dirs[index])
                val lateral = tangentOf(axis)
                val normal = axis.cross(lateral)
                val points = ArrayList<Vec3>(FILAMENT_SEGMENTS)
                for (i in 0 until FILAMENT_SEGMENTS) {
                    val t = i.toDouble() / (FILAMENT_SEGMENTS - 1).toDouble()
                    val r = lerp(radius * startRatio, coreRadius * endRatio, t)
                    val spin = 2.0 * PI * FILAMENT_TWIST * t * if ((index + wave) % 2 == 0) 1.0 else -1.0 +
                        wave * WAVE_PHASE
                    points += center.add(axis.scale(r))
                        .add(lateral.scale(cos(spin) * r * FILAMENT_SPIRAL))
                        .add(normal.scale(sin(spin) * r * FILAMENT_SPIRAL))
                }
                // 逐段铺：**段间两端各外扩 20%** 让 LINE 贴图两端渐隐的部分互相补上（否则是虚线，用户实测反馈）；
                // `stagger` 让丝线**逐段扫出**（"每个有自己的动画出现"）
                for (i in 0 until points.size - 1) {
                    val a = points[i]
                    val b = points[i + 1]
                    val ext = b.subtract(a).scale(SEGMENT_OVERLAP)
                    val from = a.subtract(ext)
                    val to = b.add(ext)
                    val segLen = from.distanceTo(to)
                    val look = ParticleVisual().style(ParticleStyle.LINE)
                        .anisoWorld(segLen.toFloat(), nominalWidth)
                        .alignTo(from, to)
                        .glowing(true)
                        .additive(true)
                    val color = gradient.colorAt(i.toDouble() / (points.size - 1).toDouble())
                    manager.create()
                        .position(from.add(to).scale(0.5))
                        .color(color)
                        .visual(look)
                        .lifetime(chargeTicks + 2)
                        .delay(i)
                        .group(group.id)
                        .spawn()
                }
            }
            val appearAt = wave * WAVE_GAP
            group.delay(appearAt)
            group.fadeIn(WAVE_FADE_IN)
            // 收束：外波先吸、内波后吸，最后一批正好在爆发前收进核心（`scale` 连"到轴心的距离"一起缩 → 缩到中心）
            val rushAt = chargeTicks - RUSH_TICKS - (WAVE_COUNT - 1 - wave) * RUSH_STAGGER
            group.delay((rushAt - appearAt - WAVE_FADE_IN).coerceAtLeast(0))
            group.scale(RUSH_SCALE, RUSH_TICKS, EasingType.EASE_IN)
            group.fadeOut(RUSH_TICKS, removeAfter = true, easing = EasingType.EASE_IN)
        }
    }

    /**
     * **向心粒子**：每一颗**自己带速度**飞向中心（`setVelocity`，两端逐 tick 自行积分），
     * 出现时刻按 `delay` 错开 —— "每个有自己的动画出现 + 逐渐汇聚"。
     */
    private fun implodeConverge(
        manager: ParticleManager,
        center: Vec3,
        coreRadius: Float,
        radius: Float,
        chargeTicks: Int,
    ) {
        val visual = dotVisual(SPARK_DOT)
        ParticleShapes.fibonacciSphere(CONVERGE_COUNT).forEachIndexed { index, dir ->
            val radial = normalize(dir)
            val startRadius = radius * (0.55 + 0.45 * ((index * 7 % CONVERGE_COUNT) / CONVERGE_COUNT.toDouble()))
            val p = center.add(radial.scale(startRadius))
            val delay = index % CONVERGE_DELAY_STEPS
            val flyTicks = (chargeTicks - delay).coerceAtLeast(1)
            val speed = (startRadius - coreRadius * 1.05) / flyTicks * CONVERGE_SPEED
            manager.create()
                .position(p)
                .color(CONVERGE_COLOR)
                .visual(visual.copy())
                .lifetime(flyTicks + 2)
                .delay(delay)
                .spawn()
                ?.setVelocity(radial.scale(-speed))
        }
    }

    // =====================================================================================
    // 爆发
    // =====================================================================================

    /** 爆发：闪光 → 小颗粒球壳撑到爆炸半径 → 内部闪电丝 / 外飞 / 壳裂 / 烟与余晖。 */
    fun burst(manager: ParticleManager, center: Vec3, radius: Float) {
        if (radius <= 0f) return
        burstFlash(manager, center, radius)
        burstShell(manager, center, radius)
        burstBolts(manager, center, radius)
        burstOutward(manager, center, radius)
        burstCracks(manager, center, radius)
        burstAfterglow(manager, center, radius)
    }

    /** 起爆闪光：几颗短命亮点（MC 的 FLASH 观感），峰值只维持 4 tick。 */
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

    /** 内部闪电丝：从中心向外的锯齿细线。 */
    private fun burstBolts(manager: ParticleManager, center: Vec3, radius: Float) {
        val group = manager.createGroup(center)
        val nominalWidth = lineWorld(BOLT_WIDTH).toFloat()
        ParticleShapes.fibonacciSphere(BOLT_COUNT).forEachIndexed { index, dir ->
            val axis = normalize(dir)
            val lateral = tangentOf(axis)
            val normal = axis.cross(lateral)
            val points = ArrayList<Vec3>(BOLT_SEGMENTS)
            for (i in 0 until BOLT_SEGMENTS) {
                val t = i.toDouble() / (BOLT_SEGMENTS - 1).toDouble()
                val jag = if (i % 2 == 0) 1.0 else -1.0
                points += center.add(axis.scale(radius * 1.02 * t))
                    .add(lateral.scale(jag * radius * 0.09 * t))
                    .add(normal.scale(if (i % 3 == 0) radius * 0.05 * t else 0.0))
            }
            Draw.polyline(manager, points, nominalWidth, true, null, true, true, ColorSource.of(BOLT_COLOR), 0, group)
        }
        group.fadeIn(1)
        group.fadeOut(SHELL_GROW_TICKS + SHELL_HOLD_TICKS, removeAfter = true, easing = EasingType.EASE_OUT)
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

    /** **壳裂**：几条沿球面的细弧向外漂散（每段一颗粒子的折线）。 */
    private fun burstCracks(manager: ParticleManager, center: Vec3, radius: Float) {
        val group = manager.createGroup(center)
        val nominalWidth = lineWorld(CRACK_WIDTH).toFloat()
        ParticleShapes.fibonacciSphere(CRACK_COUNT).forEachIndexed { index, dir ->
            val axis = normalize(dir)
            val lateral = tangentOf(axis)
            val normal = axis.cross(lateral)
            val points = ArrayList<Vec3>(CRACK_SEGMENTS)
            for (i in 0 until CRACK_SEGMENTS) {
                val t = i.toDouble() / (CRACK_SEGMENTS - 1).toDouble()
                val angle = (t - 0.5) * 1.5 + index
                val r = radius * 0.92
                points += center.add(axis.scale(r))
                    .add(lateral.scale(cos(angle) * r * 0.28))
                    .add(normal.scale(sin(angle) * r * 0.28))
            }
            Draw.polyline(
                manager, points, nominalWidth, true, null, true, true,
                ColorSource.of(SHELL_RIM_COLOR), 0, group,
            )
        }
        group.delay(SHELL_GROW_TICKS + 2)
        group.fadeIn(2)
        group.move(Vec3(0.0, radius * 0.06, 0.0), SHELL_FADE_TICKS, EasingType.EASE_OUT)
        group.fadeOut(SHELL_FADE_TICKS, removeAfter = true, easing = EasingType.EASE_OUT)
    }

    /** 余韵：少量柔边"烟"上浮 + 贴地一圈小点。 */
    private fun burstAfterglow(manager: ParticleManager, center: Vec3, radius: Float) {
        val smoke = manager.createGroup(center)
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
        smoke.fadeOut(SMOKE_FADE_TICKS, removeAfter = true, easing = EasingType.EASE_OUT)

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

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun lerp(a: Float, b: Float, t: Double): Double = a + (b - a).toDouble() * t

    /** 单位化；长度过小时给一个稳定兜底（粒子生成不能因为除零整段失败）。 */
    private fun normalize(v: Vec3): Vec3 = if (v.lengthSqr() < 1e-9) Vec3(0.0, 1.0, 0.0) else v.normalize()

    /** 取一个与 [radial] 垂直的稳定切向（不随 tick 变化 —— 录制的可复现性优先于随机感）。 */
    private fun tangentOf(radial: Vec3): Vec3 {
        val up = if (abs(radial.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        return normalize(radial.cross(up))
    }
}
