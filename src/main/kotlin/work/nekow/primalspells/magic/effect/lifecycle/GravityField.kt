package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Anchor
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ColorSource
import work.nekow.particledrawing.api.Draw
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleGroup
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleStyle
import work.nekow.particledrawing.api.ParticleVisual
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.Deferred
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spawnParticle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 重力场：服务端维持半径五格的迟缓 IV，并给穿过的魔法投射物附加向下加速度。
 * 表现分为断开的球面经线、顶部发源环、加速下压的刻度波面、落体细线和地面双井环。
 * 固定结构与波面由 PD 组逐帧编排，落体积分及成员簿记由 PD 批次维护。
 * 首 tick 和场心移动时探测下方地面，落体在该平面回收；退场撤销未落地反馈并淡出固定结构。
 */
class GravityField : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 3,
    pullPerTick = 0.0,
) {
    /** 球形边界与顶部发源环：固定成员，首 tick 铺一次，由 PD 本地自转。 */
    private var shell: ParticleGroup? = null

    /** 下坠粒子（初速 0 + 恒定向下加速度，按位置条件回收）。 */
    private var falling: ParticleBatch? = null

    /**
     * 落地溅射粒子（一次性生成、自带寿命）。
     *
     * 仍然要收进批次：它们可能**在场地结束前一瞬**才溅出来，之后还会飞 ~10 tick ——
     * 不一起清掉，就会出现「球体已经没了、地面还在溅」（实机反馈的那张图就是这个现象的另一半）。
     */
    private var splashes: ParticleBatch? = null

    /**
     * 场底平面的 y 坐标（首 tick 和场心移动时重新探测）：
     * 从场心向下探 [EFFECT_RADIUS] 格，取第一块**非空气**方块的上表面；探不到就用球体最底端。
     */
    private var floorY: Double? = null
    private var lastCenter: Vec3? = null

    /** 距上次生成重力波环的 tick 数。 */
    private var ringCooldown = 0

    /** 地面井环（永存粒子组，缓慢自转）：标出重力井的口径。 */
    private var wellRing: ParticleGroup? = null

    /** 正在下坠的重力波环（自带淡出与销毁，这里只留引用，好在场地提前结束时一并收掉）。 */
    private val rings = ArrayList<ParticleGroup>()

    /** 已经压下去几层波：第 1 层是「开场重砸」（落得更快、撞得更狠），之后才是常规节奏。 */
    private var ringsSpawned = 0

    /**
     * 已排下、但还没到点的落地溅射。
     *
     * 必须在 [clearVisuals] 里撤销：环被销毁了，可「N tick 后溅一圈」的任务还在队列里 ——
     * 不撤销就会看到**地上凭空压出一道环**（实机反馈的原话），因为那时场上已经没有球体了。
     */
    private val pendingImpacts = ArrayList<Deferred.Handle>()

    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        val random = manager.level.random
        val moved = lastCenter?.let { it.distanceToSqr(center) > 1.0e-10 } ?: false
        val previousFloor = floorY
        val floor = if (moved || floorY == null) detectFloor(manager.level, center).also { floorY = it } else floorY!!
        if (moved) {
            shell?.updateAnchor(lastCenter ?: center, center)
            wellRing?.updateAnchor(Vec3((lastCenter ?: center).x, (previousFloor ?: floor) + WELL_RING_LIFT, (lastCenter ?: center).z), Vec3(center.x, floor + WELL_RING_LIFT, center.z))
        }
        lastCenter = center

        if (shell == null) spawnBoundary(manager, center)

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
                    center.add(upper.scale((EFFECT_RADIUS * projectile.areaScale))),
                    FALL_COLOR,
                    FALL_SCALE,
                    FALL_LIFETIME_TICKS,
                    acceleration = FALL_ACCELERATION,
                    visual = ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.125f, 0.125f)
                        .alignTo(Vec3.ZERO, Vec3(0.0, -1.0, 0.0)).glowing(true).additive(false),
                )
            )
        }

        // 重力波环：每隔 RING_INTERVAL_TICKS 从场顶压下一层（加速下坠 + 边坠边淡出，到地面即消失）
        if (ringCooldown <= 0) {
            ringCooldown = RING_INTERVAL_TICKS
            // 第 1 层是开场那一下：压得最快、落地撞得最狠 —— 场地「砸下来」而不是「淡进来」
            val first = ringsSpawned == 0
            val fallTicks = if (first) RING_FIRST_FALL_TICKS else RING_FALL_TICKS
            ringsSpawned++
            spawnGravityRing(manager, center, floor, fallTicks)?.let { rings += it }
        } else {
            ringCooldown--
        }
    }

    /** 只读 PD 的权威位置判定：下坠粒子落到地面就销毁（读位置不产生任何包）。 */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        val floor = floorY ?: return
        falling?.removeIf { _, pos, _ -> pos.y <= floor }
        rings.removeAll { it.size() == 0 }
    }

    override fun clearVisuals(manager: ParticleManager, center: Vec3) {
        // 还没落地的溅射一并作废：场都没了，地面上不该再压出环来
        pendingImpacts.forEach { it.cancel() }
        pendingImpacts.clear()
        work.nekow.primalspells.magic.effect.visual.PixelVfx.retire(shell)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(manager, center, Color.ofInt(130, 215, 255),
            1600, 0.18, 10, 0.003, EFFECT_RADIUS * projectile.areaScale)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.ring(manager, center, Vec3(0.0, 1.0, 0.0),
            0.5 * projectile.areaScale, Color.ofInt(130, 215, 255), 12, 6f)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(manager, center, Color.ofInt(130, 215, 255), 128, 0.26, 14)
        shell = null
        work.nekow.primalspells.magic.effect.visual.PixelVfx.retireBatch(manager, falling, center)
        falling = null
        work.nekow.primalspells.magic.effect.visual.PixelVfx.retireBatch(manager, splashes, center)
        splashes = null
        // 取消落地回调后，井环与在途波环有限收束，避免场结束后继续产生落点冲击
        work.nekow.primalspells.magic.effect.visual.PixelVfx.retire(wellRing)
        wellRing = null
        for (ring in rings) work.nekow.primalspells.magic.effect.visual.PixelVfx.retire(ring)
        rings.clear()
    }

    /** 投射物：每 tick 附加向下的力（与 `gravity` 修正同一量级）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        projectile.status.velocity.y -= GRAVITY_PER_TICK
    }

    // ---------- 视觉构件 ----------

    /** 四条断开的经线描出球形边界，顶部环为下压波的发源面；固定几何在 PD 本地缓慢自转。 */
    private fun spawnBoundary(manager: ParticleManager, center: Vec3) {
        val group = manager.createGroup(center)
        val visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false)
        repeat(4) { meridian ->
            val azimuth = meridian * PI / 2.0
            repeat(2) { half ->
                Draw.curve(manager, { t ->
                    val angle = (0.10 + half * 0.52 + t * 0.34) * PI
                    center.add((EFFECT_RADIUS * projectile.areaScale) * sin(angle) * cos(azimuth),
                        (EFFECT_RADIUS * projectile.areaScale) * cos(angle), (EFFECT_RADIUS * projectile.areaScale) * sin(angle) * sin(azimuth))
                }, 48, ColorSource.of(SHELL_COLOR), SHELL_SCALE, group = group, visual = visual)
            }
        }
        val crownY = (EFFECT_RADIUS * projectile.areaScale) * RING_START_HEIGHT_SCALE
        val crownRadius = sqrt((EFFECT_RADIUS * projectile.areaScale) * (EFFECT_RADIUS * projectile.areaScale) - crownY * crownY)
        Draw.circle(manager, center.add(0.0, crownY, 0.0), crownRadius, 144,
            colorFn = ColorSource.of(Color.ofInt(108, 180, 242, 255)), scale = 0.65f,
            group = group, visual = visual)
        group.spin(SHELL_SPIN_AXIS, SHELL_SPIN_RADIANS_PER_TICK)
        group.anchor(Anchor.Movable(center, Vec3.ZERO, work.nekow.particledrawing.api.Orient.WORLD)).scaleTo(0f, 0).scaleTo(1f, 4, EasingType.EASE_OUT)
        shell = group
    }

    /**
     * 场底平面：从场心向下逐格探测 [(EFFECT_RADIUS * projectile.areaScale)] 格，取第一块非空气方块的上表面；
     * 一路探空（悬空施法）就退化为球体最底端 —— 保证粒子一定会被回收，不会永远下落。
     */
    private fun detectFloor(level: ServerLevel, center: Vec3): Double {
        val bottom = center.y - (EFFECT_RADIUS * projectile.areaScale)
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
        val crossSection = sqrt(((EFFECT_RADIUS * projectile.areaScale) * (EFFECT_RADIUS * projectile.areaScale) - heightAboveFloor * heightAboveFloor).coerceAtLeast(0.0))
        val radius = crossSection.coerceIn(WELL_RING_MIN_RADIUS, (EFFECT_RADIUS * projectile.areaScale))
        val group = manager.createGroup(Vec3(center.x, floor + WELL_RING_LIFT, center.z))
        Draw.circle(
            manager,
            Vec3(center.x, floor + WELL_RING_LIFT, center.z),
            radius, WELL_RING_PARTICLES,
            colorFn = WELL_RING_COLOR, scale = WELL_RING_SCALE, group = group,
            visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false),
        )
        val pivot = Vec3(center.x, floor + WELL_RING_LIFT, center.z)
        Draw.circle(manager, pivot, radius * 0.78, 120,
            colorFn = WELL_RING_COLOR, scale = 0.5f, group = group,
            visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false))
        repeat(24) { index ->
            val radial = Vec3(cos(index * PI / 12.0), 0.0, sin(index * PI / 12.0))
            Draw.line(manager, pivot.add(radial.scale(radius * 0.86)), pivot.add(radial.scale(radius * 0.95)), 4,
                colorFn = WELL_RING_COLOR, scale = 0.55f, group = group,
                visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false))
        }
        group.spin(Vec3(0.0, 1.0, 0.0), WELL_RING_SPIN_RADIANS_PER_TICK)
        group.anchor(Anchor.Movable(Vec3(center.x, floor + WELL_RING_LIFT, center.z), Vec3.ZERO, work.nekow.particledrawing.api.Orient.WORLD)).scaleTo(0f, 0).scaleTo(1f, 3, EasingType.EASE_OUT)
        wellRing = group
    }

    /**
     * 重力波环：场顶一个水平环，用 `move` 的 [EasingType.EASE_IN]（慢起快落 = 加速下坠）压到地面，
     * 实色波面落地时由 PD 销毁整组。
     *
     * 环的起始高度取球心上方 [RING_START_HEIGHT_SCALE]×半径处（该高度的水平截面半径即环半径），
     * 下落距离 = 起点到井环高度。
     */
    private fun spawnGravityRing(
        manager: ParticleManager,
        center: Vec3,
        floor: Double,
        fallTicks: Int,
    ): ParticleGroup? {
        val startY = center.y + (EFFECT_RADIUS * projectile.areaScale) * RING_START_HEIGHT_SCALE
        val radius = sqrt(
            ((EFFECT_RADIUS * projectile.areaScale) * (EFFECT_RADIUS * projectile.areaScale) - (startY - center.y) * (startY - center.y)).coerceAtLeast(0.0)
        ).coerceAtLeast(RING_MIN_RADIUS)
        val fall = startY - (floor + WELL_RING_LIFT)
        if (fall <= 0.0) return null
        val group = manager.createGroup(Vec3(center.x, startY, center.z))
        Draw.circle(
            manager, Vec3(center.x, startY, center.z), radius, RING_PARTICLES,
            colorFn = RING_COLOR, scale = RING_SCALE, group = group,
            visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false),
        )
        // 同一波面带八个向下尖的刻度，远看也能读出施力方向，而不是一圈漂浮的雪。
        repeat(8) { index ->
            val angle = index * PI / 4.0
            val p = Vec3(center.x + cos(angle) * radius, startY, center.z + sin(angle) * radius)
            val side = Vec3(-sin(angle), 0.0, cos(angle)).scale(0.13)
            val tip = p.add(0.0, -0.28, 0.0)
            for (end in listOf(p.add(side), p.subtract(side))) {
                Draw.line(manager, end, tip, 4, colorFn = RING_COLOR, scale = 0.5f, group = group,
                    visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))
            }
        }
        group.move(0.0, -fall, 0.0, fallTicks, EasingType.EASE_IN)
        group.delay((fallTicks - 3).coerceAtLeast(0)).scale(0f, 4, EasingType.EASE_IN_OUT).destroyAfter(6)
        // 落地那一拍：地面溅起一圈（一次性粒子）+ 镜头被撞一下。
        // 环的下落由 PD 在客户端本地求值，服务端只知道「[fallTicks] 之后落地」，所以这里排进延迟队列。
        pendingImpacts += Deferred.after(manager.level, fallTicks) {
            spawnImpactSplash(manager, center, floor, radius)
        }
        return group
    }

    /**
     * 波环砸到地面那一拍的**溅射**：沿井环圆周向外、略微向上炸开一圈短命粒子。
     *
     * 为什么需要它：波环自己只是「淡出消失」，落点没有反馈，下压感就断在半空。
     * 补上这一圈，观感才是「砸在地上、溅起来」（第 20 轮起不再附带镜头冲击 —— 场是「压」不是「撞」）。
     *
     * 一次性生成（自带寿命与外扩速度，不进任何集合）→ 之后**零逐 tick 包**。
     */
    private fun spawnImpactSplash(manager: ParticleManager, center: Vec3, floor: Double, radius: Double) {
        val random = manager.level.random
        val y = floor + WELL_RING_LIFT
        repeat(IMPACT_PARTICLES) {
            val angle = random.nextDouble() * PI * 2.0
            val out = Vec3(cos(angle), 0.0, sin(angle))
            val pos = Vec3(center.x + out.x * radius, y, center.z + out.z * radius)
            val batch = splashes ?: ParticleBatch(manager).also { splashes = it }
            batch.add(
                manager.spawnParticle(
                    pos,
                    IMPACT_COLOR,
                    IMPACT_SCALE,
                    IMPACT_LIFETIME,
                    // 向外 + 向上：斜着溅出去才像被砸起来，纯水平看着像环在扩
                    out.scale(IMPACT_OUT_SPEED).add(0.0, IMPACT_UP_SPEED, 0.0),
                )
            )
        }
    }

    private companion object {
        /** 生效半径：同时也是球壳标识半径 */
        const val EFFECT_RADIUS = 5.0

        // ---- 范围球壳（只做「作用范围」标识，压暗以免抢下坠粒子的视觉） ----

        /** 边界经线的小点尺寸：压暗、收细，把亮度留给下压波面 */
        const val SHELL_SCALE = 0.45f

        /** 球面经线颜色：低亮青蓝 */
        val SHELL_COLOR: Color = Color.ofInt(96, 149, 208, 255)

        /** 球壳自转轴与角速度：绕竖直轴慢转（约 10 秒一圈），客户端本地求值 → 0 包/tick */
        val SHELL_SPIN_AXIS: Vec3 = Vec3(0.0, 1.0, 0.0)
        const val SHELL_SPIN_RADIANS_PER_TICK = 0.0105

        // ---- 下坠粒子：初速 0 + 恒定向下加速度（「重力」的核心特征） ----

        /** 每 tick 生成的下坠粒子数（第 20 轮 3 → **2**：实机反馈白色粒子偏多，抢了球壳的戏） */
        const val FALLING_PER_TICK = 2

        const val FALL_SCALE = 0.55f

        /**
         * 下坠加速度（blocks/tick²）：从场顶落到场底约 10 格，
         * `t = √(2·10/0.01) ≈ 45 tick`、末速 `0.45 格/tick` —— 起步慢、落地快，正是重力的观感。
         * 调大 = 砸得更狠（同时更早回收，粒子更稀）。
         */
        val FALL_ACCELERATION: Vec3 = Vec3(0.0, -0.01, 0.0)

        /** 下坠粒子安全寿命（tick）：正常会在落地时提前销毁 */
        const val FALL_LIFETIME_TICKS = 100

        /** 下坠细线颜色：青蓝，区别于原先的白色雪点 */
        val FALL_COLOR: Color = Color.ofInt(142, 195, 242, 255)

        // ---- 重力波环 ----

        /**
         * 每隔多少 tick 压下一层重力波。
         * 第 20 轮按实机反馈从 22 提到 **12**（「把那个环的频率提高」）—— 下压的节奏变密，
         * 「重力」的存在感来自一层层压下来，而不是靠镜头震。
         */
        const val RING_INTERVAL_TICKS = 12

        /** 每层波的点数与尺寸：柔边小点组成完整波面轮廓 */
        const val RING_PARTICLES = 160
        const val RING_SCALE = 0.75f

        /** 环的起始高度（球心上方，半径的倍数）：0.62 → 该高度截面半径约 0.78×半径 */
        const val RING_START_HEIGHT_SCALE = 0.62

        /** 极度靠近场顶时截面半径趋近 0，给一个下限免得环缩成一个点 */
        const val RING_MIN_RADIUS = 1.2

        /** 一层波从场顶落到地面的时长（tick）：配合 EASE_IN 就是「越落越快」 */
        const val RING_FALL_TICKS = 26

        /** 第 1 层（开场重砸）的下落时长：比常规快一倍，落地就有反馈 */
        const val RING_FIRST_FALL_TICKS = 13

        // ---- 落地重音：溅射 + 镜头冲击 ----

        /** 落地溅射的粒子数 / 尺寸 / 寿命 / 外扩速度 / 上抛速度（第 20 轮 30 → 20，同属「白色粒子减少」） */
        const val IMPACT_PARTICLES = 20
        const val IMPACT_SCALE = 0.5f
        const val IMPACT_LIFETIME = 11
        const val IMPACT_OUT_SPEED = 0.17
        const val IMPACT_UP_SPEED = 0.11

        /** 溅射颜色：比波环亮一点，落地那一下才「亮得出来」（不再是接近纯白） */
        val IMPACT_COLOR: Color = Color.ofInt(198, 224, 246, 255)

        /** 环的颜色（偏亮的天蓝白：与淡灰下坠粒子区分开，才看得出是「一层」在压下来） */
        val RING_COLOR: ColorSource = ColorSource { t -> if (t < 0.5) RING_TINT else RING_TINT_BRIGHT }
        val RING_TINT: Color = Color.ofInt(150, 190, 235, 255)
        val RING_TINT_BRIGHT: Color = Color.ofInt(205, 230, 255, 255)

        // ---- 地面井环 ----

        /** 井环粒子数与尺寸（同样压暗：它是地面标记，不是主体） */
        const val WELL_RING_PARTICLES = 160
        const val WELL_RING_SCALE = 0.75f

        /** 井环抬高一点，免得和地面方块重叠闪烁 */
        const val WELL_RING_LIFT = 0.12

        /** 井环半径下限：三格地面投影下限：球体刚好触地时也可读，井环不参与玩法范围判断 */
        const val WELL_RING_MIN_RADIUS = 3.0

        /** 井环自转角速度（弧度/tick）：约 10 秒一圈 */
        const val WELL_RING_SPIN_RADIANS_PER_TICK = 0.0105

        val WELL_RING_COLOR: ColorSource = ColorSource { t -> if (t < 0.5) WELL_RING_TINT else WELL_RING_TINT_BRIGHT }
        val WELL_RING_TINT: Color = Color.ofInt(120, 160, 210, 255)
        val WELL_RING_TINT_BRIGHT: Color = Color.ofInt(180, 215, 250, 255)

        /** 施加给投射物的向下加速度（每 tick，与 `ConstantAcceleration` 默认值一致） */
        const val GRAVITY_PER_TICK = 0.03f
    }
}
