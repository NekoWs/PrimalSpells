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
 * 观感目标是用户给的参考（`.refs/explosion-vfx/`：8 帧 + 逐帧说明）：
 * **黑色实心球核（带亮边）+ 青色发光丝线向心收拢 → 塌成亮点 → 巨大半透明球壳撑到爆炸半径
 * （内部锯齿闪电丝）→ 壳裂成分段弧飞散 → 烟与余晖**。
 *
 * ## 尺寸口径（ParticleDrawing 1.0.13 起，别再按 scale 当格数估）
 *
 * - 渲染整宽 = 编辑器尺寸 × **0.2 格** × 贴图尺寸系数；内置贴图（[ParticleStyle]）边长 16 → 系数 1
 *   → **整宽 = scale × 0.2 格**。
 * - 内置贴图**可见尺寸小于标称**（边缘柔化必然有半透明带）：[ParticleStyle.SOFT_DOT] 可见直径
 *   ≈ **0.75 × 尺寸**、[ParticleStyle.LINE] 可见粗 ≈ **0.7 × 尺寸**（两端各 ≈ 0.2 × 长度渐隐）。
 *   本文件所有尺寸都过 [dotSize] / [lineSize] 反推，密铺间距按「≤ 0.3 × 可见直径」给。
 * - **外观没有更新通道**（零逐 tick 开销的前提）：形变一律用**组级一次性编排**
 *   （`scale` / `fadeIn` / `fadeOut` / `delay` / `move`）或逐粒子 `move` / `delay`；要换外观就 `remove()` 后重生成。
 *
 * **0 包/tick**：只在「蓄力开始」与「爆发」两拍各下发一次生成指令，之后全在客户端求值。
 */
object ExplosionFx {

    // ---------------------------------------------------------------- 尺寸换算（见类 KDoc）

    /** SOFT_DOT 可见直径 ≈ 0.75 × 标称尺寸 */
    private const val SOFT_DOT_VISIBLE = 0.75
    /** LINE 可见粗 ≈ 0.7 × 标称尺寸 */
    private const val LINE_VISIBLE = 0.7

    /**
     * 让柔边点的**可见直径**为 [blocks] 格所需的 `anisoWorld` 值。
     *
     * ⚠️ `anisoWorld` 收的是**世界格数**（`worldUnits = true`，渲染侧会自己换算成编辑器尺寸），
     * 所以这里只除可见比例、**不再乘 0.2**（第 29 轮把它当标称尺寸用过一次，黑核大了 5 倍）。
     */
    private fun dotWorld(blocks: Double): Double = blocks / SOFT_DOT_VISIBLE

    /** 让线段的**可见粗**为 [blocks] 格所需的 `anisoWorld` 值（同理：只除可见比例）。 */
    private fun lineWorld(blocks: Double): Double = blocks / LINE_VISIBLE

    /** 柔边点外观（可见直径 [blocks] 格）；[glow] 打开时叠加自发光 + 加色。 */
    private fun dotVisual(blocks: Double, glow: Boolean = true): ParticleVisual {
        val size = dotWorld(blocks).toFloat()
        return ParticleVisual().style(ParticleStyle.SOFT_DOT)
            .anisoWorld(size, size)
            .glowing(glow)
            .additive(glow)
    }

    // ---------------------------------------------------------------- 蓄力：黑核

    /** 核心球半径 = 爆炸半径 × 1/3（用户口径） */
    private const val CORE_RATIO = 1f / 3f
    /** 核亮边的点数/圈（三个正交平面各一圈 → 任意角度看都是"球核的边"） */
    private const val CORE_RING_COUNT = 72
    /** 核亮边的可见粗细（相对核心半径） */
    private const val CORE_RING_WIDTH_RATIO = 0.05
    /** 核心内爆收缩比：蓄力结束时缩到 45% */
    private const val CORE_SHRINK = 0.45f
    private val CORE_COLOR = Color.ofInt(4, 6, 12, 255)
    private val RIM_COLOR = Color.ofInt(175, 242, 255, 255)

    // ---------------------------------------------------------------- 蓄力：丝线

    private const val FILAMENT_COUNT = 6
    /** 每条丝线的折线点数（**每段一颗粒子**，故 14 → 14 颗/条） */
    private const val FILAMENT_SEGMENTS = 18
    /** 丝线起点半径（相对爆炸半径）与终点（相对核心半径） */
    private const val FILAMENT_START_RATIO = 0.95f
    private const val FILAMENT_END_RATIO = 1.25f
    /** 丝线绕核的螺旋圈数 */
    private const val FILAMENT_TWIST = 0.55
    private const val FILAMENT_SPIRAL_RADIUS = 0.35
    /** 丝线**可见**粗细（相对爆炸半径）：中间最粗、两端渐隐（`taper = true`） */
    private const val FILAMENT_WIDTH_RATIO = 0.018
    /** 蓄力结束时丝线组收缩到的比例（`scale` 连"到轴心的距离"一起缩 → 向心收拢 + 变短） */
    private const val FILAMENT_CONTRACT = 0.30f
    /** 放射状细刺（参考第 1 帧里那些又细又直的亮刺） */
    private const val SPIKE_COUNT = 10
    private val FILAMENT_INNER = Color.ofInt(228, 252, 255, 255)
    private val FILAMENT_OUTER = Color.ofInt(85, 195, 255, 255)

    // ---------------------------------------------------------------- 蓄力：向心聚拢

    private const val CONVERGE_COUNT = 34
    private const val CONVERGE_DOT_RATIO = 0.05
    private val CONVERGE_COLOR = Color.ofInt(155, 228, 255, 255)

    // ---------------------------------------------------------------- 爆发：球壳

    /** 球壳起始半径（相对爆炸半径），随后 `scale` 撑到 1.0 */
    private const val SHELL_START_RATIO = 0.32f
    /** 球壳柔边点**可见直径**（相对爆炸半径） */
    private const val SHELL_DOT_RATIO = 0.26

    /** 膜上的点比亮边点略小（膜要透、边要亮） */
    private const val FILM_DOT_SHRINK = 0.85
    /** 球壳密铺间距上限（相对可见直径）：≤ 0.3 无空隙 */
    private const val SHELL_SPACING_RATIO = 0.30
    private const val SHELL_MAX_POINTS = 900
    private const val SHELL_GROW_TICKS = 7
    private const val SHELL_HOLD_TICKS = 4
    private const val SHELL_FADE_TICKS = 9
    /**
     * 球壳颜色：**低 alpha 的半透膜**（不加色）。
     *
     * 参考里的壳是能看见后面世界的"膜"；加色混合叠几百颗只会累加成一颗过曝白球（第 29 轮实测），
     * 所以壳走普通 alpha 混合，亮边（[SHELL_RIM_COLOR]）与内部闪电丝才用加色。
     */
    private val SHELL_COLOR = Color.ofInt(205, 236, 255, 58)
    private val SHELL_RIM_COLOR = Color.ofInt(165, 232, 255, 255)

    // ---------------------------------------------------------------- 爆发：内部闪电丝

    private const val BOLT_COUNT = 9
    private const val BOLT_SEGMENTS = 11
    private const val BOLT_WIDTH_RATIO = 0.022
    private val BOLT_COLOR = Color.ofInt(215, 250, 255, 255)

    // ---------------------------------------------------------------- 爆发：外飞 / 壳裂 / 余韵

    private const val OUT_COUNT = 44
    private const val OUT_DOT_RATIO = 0.055
    private const val OUT_LIFETIME = 16
    private const val OUT_SPEED = 0.62
    private val OUT_COLOR = Color.ofInt(200, 242, 255, 255)

    private const val CRACK_COUNT = 7
    private const val CRACK_SEGMENTS = 7
    private const val CRACK_WIDTH_RATIO = 0.015

    private const val SMOKE_COUNT = 18
    private const val SMOKE_IN_TICKS = 3
    private const val SMOKE_FADE_TICKS = 10
    private const val SMOKE_DOT_RATIO = 0.42
    private val SMOKE_COLOR = Color.ofInt(118, 124, 130, 255)

    /** 各段共用的淡入时长 */
    private const val FADE_IN_TICKS = 2
    /** 塌缩时长：核心与丝线在爆发前的最后几 tick 收掉 */
    private const val COLLAPSE_TICKS = 3

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
     * 蓄力表现。所有粒子的销毁都在**客户端本地**排好（`delay` + `fadeOut(removeAfter = true)`），
     * 蓄力结束那一刻自行收干净，服务端不需要再发任何指令。
     */
    fun implode(manager: ParticleManager, center: Vec3, radius: Float, chargeTicks: Int) {
        if (radius <= 0f || chargeTicks <= 0) return
        val coreRadius = radius * CORE_RATIO
        val collapse = COLLAPSE_TICKS.coerceAtMost(chargeTicks)
        implodeCore(manager, center, coreRadius, chargeTicks, collapse)
        implodeFilaments(manager, center, coreRadius, radius, chargeTicks, collapse)
        implodeConverge(manager, center, coreRadius, radius, chargeTicks)
    }

    /**
     * **黑色实心球核 + 亮边**。
     *
     * 核用**一颗大柔边点**：柔边圆点本身就是圆的，而球从任意角度看都是圆 ——
     * 一颗「可见直径 = 核心直径」的 SOFT_DOT 就是"实心黑球"，比铺几百颗小点既准又省。
     * 亮边用三个正交平面各一圈柔边点（从任何角度看都贴着球核外沿）。
     */
    private fun implodeCore(
        manager: ParticleManager,
        center: Vec3,
        coreRadius: Float,
        chargeTicks: Int,
        collapse: Int,
    ) {
        val group = manager.createGroup(center)
        manager.create()
            .position(center)
            .color(CORE_COLOR)
            .visual(dotVisual(coreRadius * 2.0, glow = false))
            .lifetime(-1)
            .group(group.id)
            .spawn()

        val rimVisual = dotVisual(coreRadius * CORE_RING_WIDTH_RATIO)
        for (axis in listOf(Draw.Axis.XZ, Draw.Axis.XY, Draw.Axis.YZ)) {
            Draw.circle(
                manager, center, coreRadius.toDouble(), CORE_RING_COUNT, axis,
                ColorSource.of(RIM_COLOR), 1f, 0, group, rimVisual,
            )
        }

        group.fadeIn(FADE_IN_TICKS)
        // 内爆：越收越小（`scale` 连粒子尺寸一起缩）
        group.scale(CORE_SHRINK, chargeTicks, EasingType.EASE_IN)
        group.delay((chargeTicks - collapse).coerceAtLeast(0))
        group.fadeOut(collapse, removeAfter = true, easing = EasingType.EASE_IN)
    }

    /**
     * **发光丝线 + 放射细刺**。
     *
     * `Draw.polyline` 每条丝线的每一段只放**一颗**粒子：长度方向由 `anisoWorld(段长, 粗)` 拉长、
     * 朝向由 `alignTo` 对齐到该段，`taper = true` 让两端收细（"两侧尖中间略粗"）并自动用 `LINE` 贴图。
     * 于是一条丝就是**一条连续的发光丝**，不再是一排小方块。
     */
    private fun implodeFilaments(
        manager: ParticleManager,
        center: Vec3,
        coreRadius: Float,
        radius: Float,
        chargeTicks: Int,
        collapse: Int,
    ) {
        val group = manager.createGroup(center)
        val nominalWidth = lineWorld(radius * FILAMENT_WIDTH_RATIO).toFloat()
        // 沿丝线的渐变：外端青 → 内端近白
        val gradient = ColorSource { t -> FILAMENT_OUTER.lerp(FILAMENT_INNER, t.toFloat()) }

        ParticleShapes.fibonacciSphere(FILAMENT_COUNT).forEachIndexed { index, dir ->
            val axis = normalize(dir)
            val lateral = tangentOf(axis)
            val normal = axis.cross(lateral)
            val points = ArrayList<Vec3>(FILAMENT_SEGMENTS)
            for (i in 0 until FILAMENT_SEGMENTS) {
                val t = i.toDouble() / (FILAMENT_SEGMENTS - 1).toDouble()
                val r = lerp(radius * FILAMENT_START_RATIO, coreRadius * FILAMENT_END_RATIO, t)
                val spin = 2.0 * PI * FILAMENT_TWIST * t * if (index % 2 == 0) 1.0 else -1.0
                points += center.add(axis.scale(r))
                    .add(lateral.scale(cos(spin) * r * FILAMENT_SPIRAL_RADIUS))
                    .add(normal.scale(sin(spin) * r * FILAMENT_SPIRAL_RADIUS))
            }
            Draw.polyline(manager, points, nominalWidth, true, null, true, true, gradient, 0, group)
        }

        // 放射细刺
        val spikeDirs = ParticleShapes.fibonacciSphere(SPIKE_COUNT * 2)
        for (i in 0 until SPIKE_COUNT) {
            val dir = normalize(spikeDirs[i * 2])
            Draw.polyline(
                manager,
                listOf(
                    center.add(dir.scale(coreRadius * 1.15)),
                    center.add(dir.scale(radius * 0.78)),
                ),
                nominalWidth * 0.55f, true, null, true, true,
                ColorSource.of(FILAMENT_INNER), 0, group,
            )
        }

        group.fadeIn(FADE_IN_TICKS)
        // 向心收拢 + 变短：组级 `scale` 同时缩"到轴心的距离"与粒子尺寸
        group.scale(FILAMENT_CONTRACT, chargeTicks, EasingType.EASE_IN)
        group.delay((chargeTicks - collapse).coerceAtLeast(0))
        group.fadeOut(collapse, removeAfter = true, easing = EasingType.EASE_IN)
    }

    /**
     * **向心聚拢的粒子**：从壳外各方向朝核心收进去；逐粒子 `delay` 错开出现（一波接一波），
     * `EASE_IN` 让它们越靠近核心越快。
     */
    private fun implodeConverge(
        manager: ParticleManager,
        center: Vec3,
        coreRadius: Float,
        radius: Float,
        chargeTicks: Int,
    ) {
        val visual = dotVisual(radius * CONVERGE_DOT_RATIO)
        ParticleShapes.fibonacciSphere(CONVERGE_COUNT).forEachIndexed { index, dir ->
            val radial = normalize(dir)
            val startRadius = radius * (0.65 + 0.35 * ((index * 5 % CONVERGE_COUNT) / CONVERGE_COUNT.toDouble()))
            val delay = index % 4
            val handle = manager.create()
                .position(center.add(radial.scale(startRadius)))
                .color(CONVERGE_COLOR)
                .visual(visual.copy())
                .lifetime(chargeTicks)
                .delay(delay)
                .spawn() ?: return@forEachIndexed
            handle.move(
                center.add(radial.scale(coreRadius * 1.05)),
                chargeTicks - delay,
                EasingType.EASE_IN,
            )
        }
    }

    // =====================================================================================
    // 爆发
    // =====================================================================================

    /**
     * 爆发表现：**球壳从 ~0.3R 撑到爆炸半径**（组级 `scale` 一个调用写完），
     * 内部走锯齿闪电丝，壳淡出时裂成弧段飞散，最后留一点烟与余晖。
     */
    fun burst(manager: ParticleManager, center: Vec3, radius: Float) {
        if (radius <= 0f) return
        burstShell(manager, center, radius)
        burstBolts(manager, center, radius)
        burstOutward(manager, center, radius)
        burstCracks(manager, center, radius)
        burstAfterglow(manager, center, radius)
    }

    /** 球壳（含壳边亮环）：加色柔边点铺面 → "半透膜 + 亮边"。 */
    private fun burstShell(manager: ParticleManager, center: Vec3, radius: Float) {
        val shellStart = radius * SHELL_START_RATIO
        val dot: Float = (radius * SHELL_DOT_RATIO).toFloat()
        val points = shellPointCount(shellStart, dot)

        val shell = manager.createGroup(center)
        val film = ParticleVisual().style(ParticleStyle.SOFT_DOT).anisoWorld(
            dotWorld(dot.toDouble() * FILM_DOT_SHRINK).toFloat(),
            dotWorld(dot.toDouble() * FILM_DOT_SHRINK).toFloat(),
        )
        Draw.sphere(
            manager, center, shellStart.toDouble(), points,
            ColorSource.of(SHELL_COLOR), 1f, 0, shell, film,
        )
        // 亮边：三正交圈，加色 + 自发光（壳上唯一"发光"的部分）
        val rimVisual = dotVisual(dot * 0.85)
        for (axis in listOf(Draw.Axis.XZ, Draw.Axis.XY, Draw.Axis.YZ)) {
            Draw.circle(
                manager, center, shellStart.toDouble(), (points / 3).coerceIn(40, 220), axis,
                ColorSource.of(SHELL_RIM_COLOR), 1f, 0, shell, rimVisual,
            )
        }

        shell.fadeIn(1)
        shell.scale(1f / SHELL_START_RATIO, SHELL_GROW_TICKS, EasingType.EASE_OUT)
        shell.delay(SHELL_GROW_TICKS + SHELL_HOLD_TICKS)
        shell.fadeOut(SHELL_FADE_TICKS, removeAfter = true, easing = EasingType.EASE_OUT)
    }

    /** 球壳点数：按「间距 ≤ 0.3 × 可见直径」在球面上铺满，并夹上限。 */
    private fun shellPointCount(shellRadius: Float, dot: Float): Int {
        val spacing = max(dot * SHELL_SPACING_RATIO, 1.0e-3)
        val area = 4.0 * PI * shellRadius * shellRadius
        return (area / (spacing * spacing)).roundToInt().coerceIn(60, SHELL_MAX_POINTS)
    }

    /** 内部闪电丝：从中心向外走的锯齿折线（参考第 5、6 帧里的分叉亮丝）。 */
    private fun burstBolts(manager: ParticleManager, center: Vec3, radius: Float) {
        val group = manager.createGroup(center)
        val nominalWidth = lineWorld(radius * BOLT_WIDTH_RATIO).toFloat()
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

    /** 向外飞散的粒子：一次性初速，之后由 PD 两端自行积分（零逐 tick 包）。 */
    private fun burstOutward(manager: ParticleManager, center: Vec3, radius: Float) {
        val visual = dotVisual(radius * OUT_DOT_RATIO)
        ParticleShapes.fibonacciSphere(OUT_COUNT).forEach { dir ->
            val outward = normalize(dir)
            manager.create()
                .position(center.add(outward.scale(radius * 0.22)))
                .color(OUT_COLOR)
                .visual(visual.copy())
                .lifetime(OUT_LIFETIME)
                .spawn()
                ?.setVelocity(outward.scale(OUT_SPEED))
        }
    }

    /**
     * **壳裂**：球壳淡出的同时，几条**沿球面的弧段**向外漂散（参考第 7 帧）。
     * 弧段同样用折线画（每段一颗粒子），`delay` 到壳撑满之后才出现。
     */
    private fun burstCracks(manager: ParticleManager, center: Vec3, radius: Float) {
        val group = manager.createGroup(center)
        val nominalWidth = lineWorld(radius * CRACK_WIDTH_RATIO).toFloat()
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
        group.move(Vec3(0.0, radius * 0.06, 0.0), SHELL_FADE_TICKS + 4, EasingType.EASE_OUT)
        group.fadeOut(SHELL_FADE_TICKS + 4, removeAfter = true, easing = EasingType.EASE_OUT)
    }

    /**
     * 余韵：少量柔边"烟"缓慢上浮（参考第 8 帧）+ 贴地余晖。
     *
     * 烟也放进**组**里统一收场（`delay` → `fadeIn` → `fadeOut(removeAfter = true)`）：
     * 一开始只按粒子寿命自然到期，结果它活过了录制场景末尾（`pd-particles-zero` 抓到剩 18 颗）。
     */
    private fun burstAfterglow(manager: ParticleManager, center: Vec3, radius: Float) {
        val smoke = manager.createGroup(center)
        val smokeLife = SHELL_GROW_TICKS + SMOKE_IN_TICKS + SMOKE_FADE_TICKS + 2
        ParticleShapes.fibonacciSphere(SMOKE_COUNT).forEachIndexed { index, dir ->
            val radial = normalize(dir)
            val shrink = 0.8 + 0.4 * (index % 3)
            val p = center.add(radial.scale(radius * (0.15 + 0.25 * (index % 5) / 5.0)))
            val handle = manager.create()
                .position(p)
                .color(SMOKE_COLOR)
                .visual(dotVisual(radius * SMOKE_DOT_RATIO * shrink, glow = false))
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
            manager, center, radius * 0.55, 64, Draw.Axis.XZ,
            ColorSource.of(SHELL_COLOR), 1f, 0, ground, dotVisual(radius * 0.22),
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
