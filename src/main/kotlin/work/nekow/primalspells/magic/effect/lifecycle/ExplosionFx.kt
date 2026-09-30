package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ColorSource
import work.nekow.particledrawing.api.Draw
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spawnParticle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 爆炸的**表现层**：内爆蓄力（[implode]）→ 爆发（[burst]）。
 *
 * 全部由 ParticleDrawing 的**一次性指令**驱动 —— 逐粒子 `move(目标, 时长, 缓动)`、
 * 组级 `scale`/`fadeIn`/`fadeOut`，位置与缓动在**客户端逐帧求值**，本模组不逐 tick 发包、
 * 也不持有运动状态（见 [docs/architecture.md] §9）。
 *
 * ## 用户口径（第 28 轮）
 *
 * 1. **蓄力**：一个**黑色小球体**罩住爆炸半径的 **1/3**；周围一圈**蓄力线条**（两侧尖、中间略粗），
 *    随时间**向中心移动并变短**；同时有粒子**向中心快速聚拢**；
 * 2. **爆发**：一个球体在爆炸范围内出现并**放大到爆炸半径**，另有粒子向外飞出；
 * 3. **不再使用原版爆炸粒子**（`ParticleTypes.EXPLOSION*` 已全部删除）。
 *
 * ⚠️ 黑色核心在**黑底**摄影棚里看不见（那正是它的颜色）—— 录制场景为此把地面/墙换成中灰，
 * 见 `CaptureScenes.implosion`。
 *
 * 所有颜色都是**暖色**（爆炸主题）：场景判定用「暖色亮像素」而不是蓝判据。
 */
object ExplosionFx {

    // ---- 蓄力：黑色核心球 ----
    /** 核心球半径 = 爆炸半径 × 1/3（用户口径） */
    private const val CORE_RATIO = 1f / 3f
    private const val CORE_PARTICLES = 180
    private const val CORE_SCALE = 0.40f
    /** 内爆收缩比：蓄力结束时核心缩到原来的 55% */
    private const val CORE_SHRINK = 0.55f
    private val CORE_COLOR = Color.ofInt(0, 0, 0, 255)

    // ---- 蓄力：线条 ----
    private const val LINE_COUNT = 8
    private const val LINE_AXIAL_SAMPLES = 7
    private const val LINE_SCALE = 0.28f
    /** 线条起点/终点：从 0.85R 收进到核心外沿 1.15 倍处 */
    private const val LINE_START_RATIO = 0.85f
    private const val LINE_END_RATIO = 1.15f
    /** 线条半长（沿径向）：起点 0.30R → 终点缩到 1/4 */
    private const val LINE_HALF_START_RATIO = 0.30f
    private const val LINE_HALF_SHRINK = 0.25f
    private val LINE_COLOR = Color.ofInt(255, 96, 32, 235)

    // ---- 蓄力：向心聚拢的粒子 ----
    private const val CONVERGE_COUNT = 32
    private const val CONVERGE_SCALE = 0.24f
    private val CONVERGE_COLOR = Color.ofInt(228, 64, 28, 225)

    // ---- 爆发 ----
    /** 爆发球从小半径开始放大：0.35R → R */
    private const val BURST_START_RATIO = 0.35f
    private const val BURST_PARTICLES = 300
    private const val BURST_SCALE_TICKS = 6
    private const val BURST_FADE_TICKS = 6
    private const val BURST_SCALE = 0.42f
    private val BURST_COLOR = Color.ofInt(255, 232, 186, 245)

    /** 向外飞散的粒子 */
    private const val OUT_COUNT = 26
    private const val OUT_SCALE = 0.30f
    private const val OUT_LIFETIME = 14
    private const val OUT_SPEED = 0.55
    private val OUT_COLOR = Color.ofInt(255, 168, 64, 235)

    /** 各段共用的淡入/淡出时长 */
    private const val FADE_IN_TICKS = 3

    /**
     * 由半径反推蓄力时长（tick）：`半径 × 3`，夹在 4~20 tick。
     *
     * `small_explosion`(半径 3) 与 `large_explosion`(半径 6) 在各自法术里**显式**指定 8 / 18 tick
     * （用户口径 0.4 s / 0.9 s，40 / 90 毫秒级不需要自动推算），这里只服务其余爆炸（如火球命中，半径 2 → 6 tick）。
     */
    fun autoChargeTicks(radius: Float): Int = (radius * 3f).roundToInt().coerceIn(4, 20)

    /**
     * 蓄力表现：黑核心 + 蓄力线条 + 向心聚拢粒子。
     *
     * 所有粒子的寿命正好是 [chargeTicks] —— 它们在爆发那一刻自行到期，不需要额外回收。
     */
    fun implode(manager: ParticleManager, center: Vec3, radius: Float, chargeTicks: Int) {
        if (radius <= 0f || chargeTicks <= 0) return
        val core = radius * CORE_RATIO

        // 1) 黑色核心球：常驻到爆发前，边收缩边淡出（`removeAfter` 让 PD 自己排销毁）
        val coreGroup = manager.createGroup(center)
        Draw.sphere(
            manager, center, core.toDouble(), CORE_PARTICLES,
            ColorSource.of(CORE_COLOR), CORE_SCALE, group = coreGroup,
        )
        coreGroup.fadeIn(FADE_IN_TICKS)
        coreGroup.scale(CORE_SHRINK, chargeTicks, EasingType.EASE_IN)
        coreGroup.delay((chargeTicks - BURST_FADE_TICKS).coerceAtLeast(0))
        coreGroup.fadeOut(BURST_FADE_TICKS, removeAfter = true, easing = EasingType.EASE_OUT)

        // 2) 蓄力线条：8 条沿球面均匀分布的径向「尖-粗-尖」粒子串，边向内平移边缩短
        val directions = ParticleShapes.fibonacciSphere(LINE_COUNT)
        for (dir in directions) {
            val radial = normalize(dir)
            val lateral = tangentOf(radial)
            for (i in 0 until LINE_AXIAL_SAMPLES) {
                // s ∈ [-1, 1]：沿线条轴线（径向）的相对位置，两端为尖
                val s = -1.0 + 2.0 * i / (LINE_AXIAL_SAMPLES - 1)
                // 中间略粗：横向偏移的可用宽度按 (1 - |s|) 收窄
                val halfWidth = (1.0 - abs(s)) * LINE_THICKNESS_RATIO * radius
                for (v in crossOffsets(i)) {
                    val startOffset = radial.scale((LINE_START_RATIO * radius + s * LINE_HALF_START_RATIO * radius))
                        .add(lateral.scale(v * halfWidth))
                    val endOffset = radial.scale((LINE_END_RATIO * core + s * LINE_HALF_START_RATIO * radius * LINE_HALF_SHRINK))
                        .add(lateral.scale(v * halfWidth * LINE_HALF_SHRINK))
                    val handle = manager.spawnParticle(
                        center.add(startOffset), LINE_COLOR, LINE_SCALE, lifetimeTicks = chargeTicks,
                    )
                    handle?.move(center.add(endOffset), chargeTicks, EasingType.EASE_IN)
                }
            }
        }

        // 3) 向心聚拢的粒子：从壳外各方向朝核心表面加速收进去（EASE_IN = 越来越快）
        ParticleShapes.fibonacciSphere(CONVERGE_COUNT).forEachIndexed { index, dir ->
            val radial = normalize(dir)
            val startRadius = radius * (0.55 + 0.45 * ((index * 7 % CONVERGE_COUNT) / CONVERGE_COUNT.toDouble()))
            val handle = manager.spawnParticle(
                center.add(radial.scale(startRadius)), CONVERGE_COLOR, CONVERGE_SCALE, lifetimeTicks = chargeTicks,
            )
            handle?.move(center.add(radial.scale(core * 1.05)), chargeTicks, EasingType.EASE_IN)
        }
    }

    /**
     * 爆发表现：出现的球体放大到爆炸半径 + 向外飞散的粒子。
     *
     * 放大靠 `ParticleGroup.scale` —— **1.0.12 起它连「粒子到轴心的距离」一起缩放**，
     * 所以「整壳从 0.35R 胀到 R」是一个调用写完的（客户端本地求值、零带宽）。
     */
    fun burst(manager: ParticleManager, center: Vec3, radius: Float) {
        if (radius <= 0f) return

        val ball = manager.createGroup(center)
        Draw.sphere(
            manager, center, (radius * BURST_START_RATIO).toDouble(), BURST_PARTICLES,
            ColorSource.of(BURST_COLOR), BURST_SCALE, group = ball,
        )
        ball.fadeIn(1)
        ball.scale(1f / BURST_START_RATIO, BURST_SCALE_TICKS, EasingType.EASE_OUT)
        ball.delay(BURST_SCALE_TICKS)
        ball.fadeOut(BURST_FADE_TICKS, removeAfter = true, easing = EasingType.EASE_OUT)

        ParticleShapes.fibonacciSphere(OUT_COUNT).forEach { dir ->
            val outward = normalize(dir)
            manager.spawnParticle(
                center.add(outward.scale(radius * 0.20)),
                OUT_COLOR, OUT_SCALE,
                lifetimeTicks = OUT_LIFETIME,
                velocity = outward.scale(OUT_SPEED),
            )
        }
    }

    /** 线条最粗处的横向半宽（相对爆炸半径）：0.05R —— 「略粗」，不是一根扁带 */
    private const val LINE_THICKNESS_RATIO = 0.05

    /** 一条线上第 [axialIndex] 个采样点要放几颗：中间 3 颗、其余各 1 颗（两侧尖中间略粗） */
    private fun crossOffsets(axialIndex: Int): List<Double> {
        val middle = LINE_AXIAL_SAMPLES / 2
        return when {
            axialIndex == middle -> listOf(0.0, -0.55, 0.55)
            axialIndex == middle - 1 || axialIndex == middle + 1 -> listOf(-0.3, 0.3)
            else -> listOf(0.0)
        }
    }

    /** 单位化；长度过小时给一个稳定兜底（粒子生成不能因为除零整段失败）。 */
    private fun normalize(v: Vec3): Vec3 = if (v.lengthSqr() < 1e-9) Vec3(0.0, 1.0, 0.0) else v.normalize()

    /** 取一个与 [radial] 垂直的稳定切向（不随 tick 变化 —— 录制的可复现性优先于随机感）。 */
    private fun tangentOf(radial: Vec3): Vec3 {
        val up = if (abs(radial.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        return normalize(radial.cross(up))
    }
}
