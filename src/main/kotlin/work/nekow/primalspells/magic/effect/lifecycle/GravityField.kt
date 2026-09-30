package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ColorSource
import work.nekow.particledrawing.api.Draw
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleGroup
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spawnParticle
import kotlin.math.sqrt

/**
 * 重力场 —— 半径 5 的重力区域：
 * - **生物**：每 tick 刷新迟缓 IV（离开范围立刻解除）；
 * - **魔法投射物**：每 tick 附加一份向下的力（[GRAVITY_PER_TICK]），穿过区域的法术会下坠；
 * - **粒子**（ParticleDrawing）分三层，都围绕「**下坠是被加速的**」这一个核心观感：
 *   1. **范围球壳**：半径 [EFFECT_RADIUS] 的球面上均匀铺 [SHELL_PARTICLES] 个半透明淡蓝灰粒子并永存，
 *      只作为**作用范围**标识（场本身静止，不需要每 tick 跟踪；场结束时统一销毁）；
 *   2. **下坠粒子**：每 tick 在**上半球表面**生成 [FALLING_PER_TICK] 个淡灰粒子，
 *      初速为 **0**、并施加恒定向下加速度 [FALL_ACCELERATION]（`spawnParticle(..., acceleration = ...)`，
 *      施力一次、之后服务端与客户端各自逐 tick 积分）—— 于是粒子**越落越快**，
 *      落到地面（[floorY]）即销毁；
 *   3. **重力波环**：每 [RING_INTERVAL_TICKS] tick 在场顶生成一个水平粒子环（[Draw.circle]），
 *      用 `move` + `EASE_IN` **加速**下坠，边坠边淡出 —— 一层层「压下来」的面，给场一个明确的**方向感**；
 *      场底再常驻一个缓慢自转的**井环**（[Draw.circle] + `spin`）标出重力井的口径。
 *
 * ## 为什么改掉了原来的表现（本轮的「重做重力场视觉」）
 *
 * 旧表现是「上半球生成、**匀速**垂直下落」——匀速下落正是**雪花**的运动特征，所以看起来像下雪。
 * 重力的可辨识特征有两件事，这次都补上了：
 * - **加速度**：落体必须越落越快（初速 0 + 恒定力，而不是一票到底的匀速）；
 * - **方向性/层次**：用一层层加速下坠的波环 + 地面井环，把「有个东西在往下压」画出来。
 *
 * ## 地面：为什么不再用「下半球表面」回收
 *
 * 场的中心是法术落点（常在**半空**），下半球表面往往埋在地里，粒子「在半空/地里凭空消失」既不可见也不可信。
 * 现在首 tick 用一次向下探测求出**场底平面** [floorY]（探测不到实地就退化为球体最底端），
 * 粒子与波环都落到这个平面为止 —— 看得见的「砸在地上」。
 *
 * 成员簿记交给 PD 的 [ParticleBatch]（球壳 `ensureSize` 补齐、下坠粒子 `removeIf` 按位置回收）；
 * 位置积分/同步/渲染由 ParticleDrawing 负责（见 `docs/conventions.md` §9 与 `docs/design/effects-api.md` §0）。
 */
class GravityField : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 3,
    pullPerTick = 0.0,
) {
    /** 范围球壳（永存粒子）：首 tick 铺满，之后只在 PD 侧有损耗时补齐。 */
    private var shell: ParticleBatch? = null

    /** 下坠粒子（初速 0 + 恒定向下加速度，按位置条件回收）。 */
    private var falling: ParticleBatch? = null

    /**
     * 场底平面的 y 坐标（首 tick 探测一次）：
     * 从场心向下探 [EFFECT_RADIUS] 格，取第一块**非空气**方块的上表面；探不到就用球体最底端。
     */
    private var floorY: Double? = null

    /** 距上次生成重力波环的 tick 数。 */
    private var ringCooldown = 0

    /** 地面井环（永存粒子组，缓慢自转）：标出重力井的口径。 */
    private var wellRing: ParticleGroup? = null

    /** 正在下坠的重力波环（自带淡出与销毁，这里只留引用，好在场地提前结束时一并收掉）。 */
    private val rings = ArrayList<ParticleGroup>()

    /** 球壳偏移（相对中心，半径 [EFFECT_RADIUS]）：Fibonacci 球结果确定，算一次即可。 */
    private val shellOffsets: List<Vec3> by lazy {
        ParticleShapes.fibonacciSphere(SHELL_PARTICLES).map { it.scale(EFFECT_RADIUS) }
    }

    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        val random = manager.level.random
        val floor = floorY ?: detectFloor(manager.level, center).also { floorY = it }

        val shellBatch = shell ?: ParticleBatch(manager).also { shell = it }
        // 均匀球壳（Fibonacci 球）：点到点间距近似相等，才像「范围标识」
        shellBatch.ensureSize(SHELL_PARTICLES) { index ->
            manager.spawnParticle(center.add(shellOffsets[index]), SHELL_COLOR, SHELL_SCALE)
        }

        // 地面井环：铺一次，之后由 PD 在客户端本地自转（0 包/tick）
        if (wellRing == null) spawnWellRing(manager, center, floor)

        val fallingBatch = falling ?: ParticleBatch(manager).also { falling = it }
        repeat(FALLING_PER_TICK) {
            // 上半球表面：把随机球面方向翻到 y ≥ 0
            val dir = ParticleShapes.randomUnitVector(random)
            val upper = if (dir.y < 0.0) dir.scale(-1.0) else dir
            // 初速 0 + 恒定向下加速度：位置由 PD 逐 tick 积分（零逐 tick 包），越落越快才有「重力」的样子
            fallingBatch.add(
                manager.spawnParticle(
                    center.add(upper.scale(EFFECT_RADIUS)),
                    FALL_COLOR,
                    FALL_SCALE,
                    FALL_LIFETIME_TICKS,
                    acceleration = FALL_ACCELERATION,
                )
            )
        }

        // 重力波环：每隔 RING_INTERVAL_TICKS 从场顶压下一层（加速下坠 + 边坠边淡出，到地面即消失）
        if (ringCooldown <= 0) {
            ringCooldown = RING_INTERVAL_TICKS
            spawnGravityRing(manager, center, floor)?.let { rings += it }
        } else {
            ringCooldown--
        }
    }

    /** 只读 PD 的权威位置判定：下坠粒子落到地面就销毁（读位置不产生任何包）。 */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        val floor = floorY ?: return
        falling?.removeIf { _, pos, _ -> pos.y <= floor }
    }

    override fun clearVisuals(manager: ParticleManager, center: Vec3) {
        shell?.clear()
        falling?.clear()
        // 井环与还在下坠的波环都是粒子组：直接销毁（场地结束不需要退场动画，粒子随组一起消失）
        wellRing?.remove()
        wellRing = null
        for (ring in rings) ring.remove()
        rings.clear()
    }

    /** 投射物：每 tick 附加向下的力（与 `gravity` 修正同一量级）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        projectile.status.velocity.y -= GRAVITY_PER_TICK
    }

    // ---------- 视觉构件 ----------

    /**
     * 场底平面：从场心向下逐格探测 [EFFECT_RADIUS] 格，取第一块非空气方块的上表面；
     * 一路探空（悬空施法）就退化为球体最底端 —— 保证粒子一定会被回收，不会永远下落。
     */
    private fun detectFloor(level: ServerLevel, center: Vec3): Double {
        val bottom = center.y - EFFECT_RADIUS
        var y = Mth.floor(center.y) - 1
        while (y.toDouble() >= bottom) {
            val probe = BlockPos(Mth.floor(center.x), y, Mth.floor(center.z))
            if (!level.getBlockState(probe).isAir) return (y + 1).toDouble()
            y--
        }
        return bottom
    }

    /** 场底井环：地面上一个缓慢自转的水平粒子环，半径随场底高度收缩（球体在该高度的水平截面）。 */
    private fun spawnWellRing(manager: ParticleManager, center: Vec3, floor: Double) {
        val heightAboveFloor = center.y - floor
        val crossSection = sqrt((EFFECT_RADIUS * EFFECT_RADIUS - heightAboveFloor * heightAboveFloor).coerceAtLeast(0.0))
        val radius = crossSection.coerceIn(WELL_RING_MIN_RADIUS, EFFECT_RADIUS)
        val group = manager.createGroup(Vec3(center.x, floor + WELL_RING_LIFT, center.z))
        Draw.circle(
            manager,
            Vec3(center.x, floor + WELL_RING_LIFT, center.z),
            radius, WELL_RING_PARTICLES,
            colorFn = WELL_RING_COLOR, scale = WELL_RING_SCALE, group = group,
        )
        group.spin(Vec3(0.0, 1.0, 0.0), WELL_RING_SPIN_RADIANS_PER_TICK)
        wellRing = group
    }

    /**
     * 重力波环：场顶一个水平环，用 `move` 的 [EasingType.EASE_IN]（慢起快落 = 加速下坠）压到地面，
     * 同时 `fadeOut` 淡出并在动画结束后由 PD 自行销毁整组。
     *
     * 环的起始高度取球心上方 [RING_START_HEIGHT_SCALE]×半径处（该高度的水平截面半径即环半径），
     * 下落距离 = 起点到井环高度。
     */
    private fun spawnGravityRing(manager: ParticleManager, center: Vec3, floor: Double): ParticleGroup? {
        val startY = center.y + EFFECT_RADIUS * RING_START_HEIGHT_SCALE
        val radius = sqrt(
            (EFFECT_RADIUS * EFFECT_RADIUS - (startY - center.y) * (startY - center.y)).coerceAtLeast(0.0)
        ).coerceAtLeast(RING_MIN_RADIUS)
        val fall = startY - (floor + WELL_RING_LIFT)
        if (fall <= 0.0) return null
        val group = manager.createGroup(Vec3(center.x, startY, center.z))
        Draw.circle(
            manager, Vec3(center.x, startY, center.z), radius, RING_PARTICLES,
            colorFn = RING_COLOR, scale = RING_SCALE, group = group,
        )
        group.move(0.0, -fall, 0.0, RING_FALL_TICKS, EasingType.EASE_IN)
        group.fadeOut(RING_FALL_TICKS, removeAfter = true)
        return group
    }

    private companion object {
        /** 生效半径：同时也是球壳标识半径 */
        const val EFFECT_RADIUS = 5.0

        // ---- 范围球壳（只做「作用范围」标识，压暗以免抢下坠粒子的视觉） ----

        /** 范围球壳粒子数（永存） */
        const val SHELL_PARTICLES = 96

        const val SHELL_SCALE = 0.4f

        val SHELL_COLOR: Color = Color.ofInt(150, 175, 205, 70)

        // ---- 下坠粒子：初速 0 + 恒定向下加速度（「重力」的核心特征） ----

        /** 每 tick 生成的下坠粒子数 */
        const val FALLING_PER_TICK = 3

        const val FALL_SCALE = 0.55f

        /**
         * 下坠加速度（blocks/tick²）：从场顶落到场底约 10 格，
         * `t = √(2·10/0.01) ≈ 45 tick`、末速 `0.45 格/tick` —— 起步慢、落地快，正是重力的观感。
         * 调大 = 砸得更狠（同时更早回收，粒子更稀）。
         */
        val FALL_ACCELERATION: Vec3 = Vec3(0.0, -0.01, 0.0)

        /** 下坠粒子安全寿命（tick）：正常会在落地时提前销毁 */
        const val FALL_LIFETIME_TICKS = 100

        /** 下坠粒子颜色：淡灰 */
        val FALL_COLOR: Color = Color.ofInt(205, 205, 205)

        // ---- 重力波环 ----

        /** 每隔多少 tick 压下一层重力波 */
        const val RING_INTERVAL_TICKS = 22

        /** 每层环的粒子数与尺寸 */
        const val RING_PARTICLES = 44
        const val RING_SCALE = 0.45f

        /** 环的起始高度（球心上方，半径的倍数）：0.62 → 该高度截面半径约 0.78×半径 */
        const val RING_START_HEIGHT_SCALE = 0.62

        /** 极度靠近场顶时截面半径趋近 0，给一个下限免得环缩成一个点 */
        const val RING_MIN_RADIUS = 1.2

        /** 一层波从场顶落到地面的时长（tick）：配合 EASE_IN 就是「越落越快」 */
        const val RING_FALL_TICKS = 26

        /** 环的颜色（偏亮的天蓝白：与淡灰下坠粒子区分开，才看得出是「一层」在压下来） */
        val RING_COLOR: ColorSource = ColorSource { t -> RING_TINT.lerp(RING_TINT_BRIGHT, t.toFloat()) }
        val RING_TINT: Color = Color.ofInt(150, 190, 235, 150)
        val RING_TINT_BRIGHT: Color = Color.ofInt(205, 230, 255, 190)

        // ---- 地面井环 ----

        /** 井环粒子数与尺寸 */
        const val WELL_RING_PARTICLES = 56
        const val WELL_RING_SCALE = 0.5f

        /** 井环抬高一点，免得和地面方块重叠闪烁 */
        const val WELL_RING_LIFT = 0.12

        /** 井环半径下限：场底恰好在球体最底端时截面半径为 0 */
        const val WELL_RING_MIN_RADIUS = 0.8

        /** 井环自转角速度（弧度/tick）：约 10 秒一圈 */
        const val WELL_RING_SPIN_RADIANS_PER_TICK = 0.0105

        val WELL_RING_COLOR: ColorSource = ColorSource { t -> WELL_RING_TINT.lerp(WELL_RING_TINT_BRIGHT, t.toFloat()) }
        val WELL_RING_TINT: Color = Color.ofInt(120, 160, 210, 130)
        val WELL_RING_TINT_BRIGHT: Color = Color.ofInt(180, 215, 250, 175)

        /** 施加给投射物的向下加速度（每 tick，与 `ConstantAcceleration` 默认值一致） */
        const val GRAVITY_PER_TICK = 0.03f
    }
}
