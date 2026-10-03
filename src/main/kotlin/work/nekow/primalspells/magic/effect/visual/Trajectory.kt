package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleVisual
import work.nekow.primalspells.client.render.ParticleTextures
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.pdParticles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 拖尾特效：Noita「火花弹」式的分层粒子尾迹（替换原来「每 tick 两颗随机小点」的写法）。
 *
 * ## 三层结构（一次 [onTick] 里按「谁更靠前」的顺序铺）
 *
 * | 层 | 贴图 | 位置 | 运动 | 读数 |
 * |---|---|---|---|---|
 * | **bead 白热核** | `LINE` 贴图 + 方比例 | `[-0.05R, -0.30R]` 段上（贴着本体） | 随投射物同向、略快 → 被本体甩在后面 | 一颗过曝白黄亮核 |
 * | **filament 丝线** | `LINE`（两端渐隐） | `[-0.02R, -0.38R]` 段上（本体后一小段） | 近零速 → 几乎钉在被抛下的位置 | 白热 → 主色的连续亮芯 |
 * | **ember 余烬** | `SOFT_DOT` | `[-0.12R, -1.05R]` 段上，带侧向散开 | 反向初速 + 侧向扩散 + **恒定下坠力** | 主色 → 压暗主色的火花 |
 *
 * 三件事让它读起来「细」而不是「一串点」：
 * 1. **贴图**：`LINE` 两端各渐隐 ≈0.2×长度、`SOFT_DOT` 边缘柔化 —— 不再是一排硬边方块；
 * 2. **运动**：丝线层近零速「钉」在轨迹上、余烬层才反向散开并受重力 —— 于是一根**连续的亮芯**
 *    外面挂一圈**向外飘落的火花**，而不是均匀的一串珠子；
 * 3. **颜色**：每层都在「白热」与法术主色之间插值（[color] 仍是各法术原来的输入），
 *    头部更白、尾部更接近主色并被压暗 —— 明暗有层次。
 *
 * ## 静止时（黑洞 / 重力场 / `delayed_cast`）
 *
 * 速度 ≈ 0 时不铺尾迹，改撒**缓漂的余烬 + 偶发白核**（[emitAmbient]）：位置按黄金角摊在球面上、
 * 速度朝外且很慢、寿命随机，读起来是「一颗在缓慢吐火星的球」，而不是原地点几个点。
 *
 * ## 带宽
 *
 * 每颗粒子只在**生成那次**下发（位置 + 一次速度 + 一次恒定力），之后位置积分、寿命回收、渲染全归 PD：
 * 全程**零逐 tick 位置包**（对比 `handle.track` 驱动是每 tick 每粒子一包）。
 *
 * ## 随机数是**确定性**的（[hash01]）
 *
 * 不用 `Random`：同一颗投射物在同一 tick 永远得到同一组偏移。理由是本项目的实机录制
 * （`tools/capture/`）与离线自检要求**可复现**，纯随机会让同一份代码两次录出不同画面。
 * 取偏移用「整数哈希 + 位段」而不是递推序列，避免相邻粒子取到相邻值而聚成串。
 *
 * 阶段：[EffectPhase.VISUAL] —— 在位置积分之后执行，读到本 tick 的最终位置与速度。
 *
 * @param color     拖尾**主色**（各法术原本传入的颜色：火球橙、激光青、黑洞紫…）
 * @param intensity 整体倍率（粒子数与尺寸同乘），默认 1；给 0 即完全关闭该法术的尾迹
 */
class Trajectory(
    val color: Color,
    val intensity: Float = 1f,
) : BaseEffect() {
    override val phase = EffectPhase.VISUAL

    /** 上一 tick 的位置：尾迹沿「上一帧 → 本帧」这段位移铺，而不是全挤在本帧的头部。 */
    private var prevPos: Vec3? = null

    /** 本效果的稳定身份（哈希里"哪一颗投射物"那一维），避免同一 tick 的多颗投射物形状雷同。 */
    private var seed: Int = 0

    override fun onActive() {
        seed = System.identityHashCode(this)
    }

    override fun onTick() {
        val level = caster.level() as? ServerLevel ?: return
        val manager = wand.pdParticles(level)
        val pos = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        val velocity = Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble())
        val from = prevPos ?: pos
        prevPos = pos

        val speed = velocity.length()
        if (speed < MIN_SPEED) {
            emitAmbient(manager, pos)
            return
        }

        val dir = velocity.scale(1.0 / speed)
        // 与运动方向垂直的一对基向量：侧向散开 / 圆周偏移都在这个平面里取
        val side = perpendicular(dir)
        val up = dir.cross(side).normalize()
        val speedFactor = (speed / SPEED_FOR_FULL).coerceIn(0.0, 1.0).toFloat()
        val strength = (intensity * (0.55f + 0.45f * speedFactor)).coerceIn(0f, 1f)

        // ⚠️ **锚点必须用上一 tick 的位置 [from]，不能用本 tick 的 [pos]**（第 47 轮修）：
        //   本体粒子走 `handle.track(...)`，PD 在客户端按 `TrackBuffer` 的 `previous → current`
        //   两端点做 partialTick 插值 ⇒ 屏幕上看到的本体位置**永远落后权威位置最多一整 tick**
        //   （`partialTick = 0` 时正好落后一格位移）。而尾迹粒子是「生成即静止」、按绝对位置渲染的
        //   ⇒ 若拿 [pos] 当锚点，尾迹最前那一颗会**系统性地跑到本体前面一格**
        //   （用户实机截图圈出来的就是它）。锚点放到 [from] 上，两者就落在同一个插值端点上、一起前进。
        val anchor = from
        // ① 扫过的余痕：从锚点沿来路往后铺一串**逐渐变小变淡**的符文菱形 —— 尾迹的主体与轮廓
        emitSmear(manager, anchor, pos, dir, strength)
        // ② 锚点上的那颗白热符文核：把「法术弹在哪」点出来（与本体粒子的渲染位置对齐）
        emitCore(manager, anchor)
        // ③ 贴着弹体、快速向附近散开又很快消失的细小符文（只做质感，不做长度）
        emitMotes(manager, anchor, dir, side, up, strength)
    }

    /**
     * 释放完毕就地补一小撮余烬：让「法术消失」也有一拍停顿，而不是粒子说没就没。
     *
     * ⚠️ 寿命**必须错开**（[DEATH_LIFETIME_MIN]..[DEATH_LIFETIME_MAX]）：早先给的是同一个常数，
     * 结果整批火花在同一 tick 到点、全局曲线是个**悬崖** —— 实机录制的退场判据
     * （`50% → 5%` 的跨度 ≥4 帧）当场把它抓出来了。错开之后是逐颗收掉的**渐隐**。
     */
    override fun onDie() {
        val level = caster.level() as? ServerLevel ?: return
        val manager = wand.pdParticles(level)
        val pos = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        val velocity = Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble())
        val speed = velocity.length()
        val dir = if (speed > MIN_SPEED) velocity.scale(1.0 / speed) else Vec3(0.0, 1.0, 0.0)
        val side = perpendicular(dir)
        val up = dir.cross(side).normalize()
        if (speed < MIN_SPEED) {
            emitFieldAfterglow(manager, pos, side, up)
            prevPos = null
            return
        }
        repeat(DEATH_SPARKS) { i ->
            val theta = GOLDEN_ANGLE * i + hash01(seed, i + 401) * TAU
            val outward = side.scale(cos(theta)).add(up.scale(sin(theta)))
            val drift = outward.normalize().scale(DEATH_SPEED * (0.5 + hash01(seed, i + 421))).add(dir.scale(-DEATH_SPEED * 0.4))
            val lifetime = DEATH_LIFETIME_MIN +
                (hash01(seed, i + 481) * (DEATH_LIFETIME_MAX - DEATH_LIFETIME_MIN)).toInt()
            val size = MOTE_SIZE_MIN + (MOTE_SIZE_MAX - MOTE_SIZE_MIN) * hash01(seed, i + 461)
            manager.create()
                .position(pos)
                .color(hotColor(0.55f + 0.45f * hash01(seed, i + 441).toFloat()))
                .visual(runeVisual(size * 1.6))
                .lifetime(lifetime)
                .spawn()
                ?.setVelocity(drift)
                ?.applyForce(FALLING, FORCE_FOREVER)
        }
        prevPos = null
    }

    // =====================================================================================
    // 三层（① 余痕 / ② 白热核 / ③ 细碎符文）
    // =====================================================================================

    /**
     * ① **扫过的余痕**（第 45 轮用户选定的形状）：从**锚点**（= 上一 tick 的权威位置，见 [onTick]）
     * 沿来路往后，铺一串**逐渐变小变淡**的符文菱形。
     *
     * 为什么这样能得到「清晰的轮廓」：
     * - 余痕由**沿轨迹排列**的菱形组成（不是围着路径乱散的点云）⇒ 读成"一道划过的痕迹"；
     * - 每颗菱形都是**广告牌**（`ParticleVisual` 默认 billboard = true）⇒ 任何视角都正面朝向相机，
     *   ⚠️ **别给这一层加 `alignTo`**：它会强制 `billboard = false`，菱形就变成固定朝向的**薄片**
     *   （第 46 轮用户实机反馈「正视几乎就是一个薄片」）。符文是对称菱形，串形状靠的是
     *   位置 + 尺寸 + 亮度的梯度，不需要旋转它；
     * - 尺寸与亮度按「离本体多远」单调递减（贴着本体的最大最亮）⇒ 有明确的方向与锥度。
     *
     * 速度**不给**：粒子留在原地由 PD 积分，本体继续飞 —— 「扫过」这个语义是位置关系带来的，
     * 不需要速度，所以这一层也是 0 逐 tick 包。
     *
     * @param anchor 锚点（上一 tick 的权威位置）—— 与本体粒子的插值端点对齐，见 [onTick]
     * @param pos    本 tick 的权威位置（只用来量「这一 tick 走了多远」，决定余痕的纵向展开）
     */
    private fun emitSmear(manager: ParticleManager, anchor: Vec3, pos: Vec3, dir: Vec3, strength: Float) {
        if (strength <= 0f) return
        val lateral = perpendicular(dir)
        // 这一 tick 本体走了多远：余痕的总纵向展开量（静止/极慢时退化为不展开，不会铺成一条线）
        val travel = pos.distanceTo(anchor)
        repeat(SMEAR_COUNT) { i ->
            // t = 0 贴着本体（最大最亮）→ t = 1 最远最淡
            val t = i.toDouble() / (SMEAR_COUNT - 1).coerceAtLeast(1)
            val along = anchor.subtract(dir.scale(travel * t))
            val jitter = (hash01(seed, status.age + i + 601) - 0.5) * SMEAR_JITTER
            val center = along.add(lateral.scale(jitter))
            val visual = runeVisual(SMEAR_SIZE_MAX - (SMEAR_SIZE_MAX - SMEAR_SIZE_MIN) * t)
            // 越远越淡：hot 从 0.55 递减到 0.05（配合 hotColor 的增益曲线）
            val hot = ((SMEAR_HOT_NEAR - (SMEAR_HOT_NEAR - SMEAR_HOT_FAR) * t) * strength).toFloat()
            manager.create()
                .position(center)
                .color(hotColor(hot))
                .visual(visual)
                .lifetime(SMEAR_LIFETIME)
                .spawn()
        }
    }

    /** ② 本体上当帧的一颗**白热符文核**：把「法术弹在哪」点出来（柔光晕已按用户口径删除）。 */
    private fun emitCore(manager: ParticleManager, pos: Vec3) {
        if (intensity <= 0f) return
        val visual = runeVisual(CORE_SIZE)
        manager.create()
            .position(pos)
            .color(hotColor(1f))
            .visual(visual)
            .lifetime(CORE_LIFETIME)
            .spawn()
    }

    /**
     * ③ **细碎符文**：贴着弹体、快速向附近散开、很快消失的小菱形（只做质感，不做长度）。
     *
     * 尺寸 0.05~0.09 格、速度 0.10~0.22 格/tick、寿命 2~5 tick ⇒ `v·t` 只有 0.2~1.0 格，
     * 散开一小段就没了 —— 用户口径「很细小的粒子很快的向附近散开消失」。
     * 出生带压在本体后方 0.05~0.45 格，不参与"拉长尾迹"（那是 [emitSmear] 的活）。
     */
    private fun emitMotes(
        manager: ParticleManager,
        pos: Vec3,
        dir: Vec3,
        side: Vec3,
        up: Vec3,
        strength: Float,
    ) {
        if (strength <= 0f) return
        val count = (MOTE_BASE * intensity * (0.6f + 0.4f * strength)).roundToInt().coerceIn(1, MOTE_MAX)
        repeat(count) { i ->
            val back = MOTE_BACK_MIN + (MOTE_BACK_MAX - MOTE_BACK_MIN) * hash01(seed, status.age + i + 91)
            val along = pos.subtract(dir.scale(back))
            val theta = GOLDEN_ANGLE * i + hash01(seed, status.age + i + 111) * TAU
            val radius = MOTE_RADIUS * sqrt(hash01(seed, status.age + i + 131))
            val offset = side.scale(radius * cos(theta)).add(up.scale(radius * sin(theta)))
            val spawn = along.add(offset)
            val outward = if (offset.lengthSqr() < 1e-9) side else offset.normalize()
            val speed = MOTE_SPEED_MIN + (MOTE_SPEED_MAX - MOTE_SPEED_MIN) * hash01(seed, status.age + i + 151)
            val size = MOTE_SIZE_MIN + (MOTE_SIZE_MAX - MOTE_SIZE_MIN) * hash01(seed, status.age + i + 211)
            // 亮度压到 0.18~0.62：火花是「余光」，不能抢过余痕与本体的对比（用户口径「飞出去的小粒子太扎眼」）
            val hot = 0.18f + 0.44f * hash01(seed, status.age + i + 191).toFloat()
            val visual = runeVisual(size)
            manager.create()
                .position(spawn)
                .color(hotColor(hot * strength))
                .visual(visual)
                .lifetime(
                    MOTE_LIFETIME_MIN +
                        (hash01(seed, status.age + i + 231) * (MOTE_LIFETIME_MAX - MOTE_LIFETIME_MIN)).toInt()
                )
                .spawn()
                ?.setVelocity(outward.scale(speed).add(dir.scale(-MOTE_BACK_SPEED)))
                ?.applyForce(FALLING, FORCE_FOREVER)
        }
    }

    /**
     * **静止场的退场余晖**（黑洞 / 重力场这类 `speed = 0` 的投射物，第 47 轮补）。
     *
     * 为什么必须单独有一条：飞行的法术有「余痕」这条尾巴，本体一死，尾迹会在 [SMEAR_LIFETIME]
     * 里逐颗到点 ⇒ 退场天然是渐隐（实机判据量到 5~9 帧）。而**静止场没有尾迹** ——
     * 它的粒子全靠 [emitAmbient] 每 tick 现撒，本体一死就**断供**，屏幕上剩下的几颗寿命又短，
     * 于是退场判据量到 `50%→5%` 只跨 **3 帧**（要求 ≥4）。这条余晖就是给它补一段"慢慢收掉"。
     *
     * 手法与 [emitSmear] 一致：**寿命错开 + 从小到大、从亮到暗**，位置摊在球壳上，
     * 速度朝外很慢 —— 读起来是"场把自己散掉了"，而不是"啪一下关灯"。
     */
    private fun emitFieldAfterglow(manager: ParticleManager, pos: Vec3, side: Vec3, up: Vec3) {
        repeat(AFTERGLOW_DOTS) { i ->
            val forward = side.cross(up).normalize()
            val theta = GOLDEN_ANGLE * i + hash01(seed, i + 811) * TAU
            val z = 2.0 * hash01(seed, i + 821) - 1.0
            val r = sqrt(max(0.0, 1.0 - z * z))
            val outward = side.scale(r * cos(theta)).add(up.scale(r * sin(theta))).add(forward.scale(z))
            val spawn = pos.add(outward.scale(AFTERGLOW_RADIUS_MIN + (AFTERGLOW_RADIUS_MAX - AFTERGLOW_RADIUS_MIN) * hash01(seed, i + 831)))
            val lifetime = AFTERGLOW_LIFETIME_MIN +
                (hash01(seed, i + 841) * (AFTERGLOW_LIFETIME_MAX - AFTERGLOW_LIFETIME_MIN)).toInt()
            val size = AFTERGLOW_SIZE_MIN + (AFTERGLOW_SIZE_MAX - AFTERGLOW_SIZE_MIN) * hash01(seed, i + 851)
            manager.create()
                .position(spawn)
                .color(hotColor(0.6f + 0.4f * hash01(seed, i + 861).toFloat()))
                .visual(runeVisual(size))
                .lifetime(lifetime)
                .spawn()
                ?.setVelocity(outward.scale(AFTERGLOW_SPEED * (0.4 + 0.9 * hash01(seed, i + 871))))
        }
    }

    /**
     * **静止投射物的氛围**：缓慢漂散的**细小符文** + 偶发一颗白热核。
     *
     * 不做「原地抖一下」那种写法：位置按黄金角铺在球面上、速度朝外且很慢，
     * 于是球形场（黑洞 / 重力场）读起来是**一颗在缓慢吐符文的球**，静止的 `delayed_cast` 也有一层浮尘。
     */
    private fun emitAmbient(manager: ParticleManager, pos: Vec3) {
        if (intensity <= 0f) return
        val dust = (AMBIENT_BASE * intensity).roundToInt().coerceIn(1, AMBIENT_MAX)
        repeat(dust) { i ->
            val outward = spherePoint(i, 251)
            val spawn = pos.add(outward.scale(AMBIENT_RADIUS_MIN + (AMBIENT_RADIUS_MAX - AMBIENT_RADIUS_MIN) * hash01(seed, status.age + i + 291)))
            manager.create()
                .position(spawn)
                .color(hotColor(0.35f * intensity))
                .visual(runeVisual(AMBIENT_DOT))
                .lifetime(AMBIENT_LIFETIME)
                .spawn()
                ?.setVelocity(outward.scale(AMBIENT_SPEED * (0.4 + 0.9 * hash01(seed, status.age + i + 311))))
        }
        if (hash01(seed, status.age / AMBIENT_MOTE_PERIOD + 701) < AMBIENT_MOTE_CHANCE) {
            val spawn = pos.add(spherePoint(status.age, 331).scale(AMBIENT_MOTE_RADIUS))
            manager.create()
                .position(spawn)
                .color(hotColor(0.9f))
                .visual(runeVisual(CORE_SIZE))
                .lifetime(AMBIENT_MOTE_LIFETIME)
                .spawn()
        }
    }

    // =====================================================================================
    // 外观与颜色
    // =====================================================================================

    /**
     * 白热 ↔ 主色：`hot` 越大越白（头部），越小越接近法术主色并压暗（尾部）。
     *
     * **亮度只能在这里定**：PD 没有「逐 tick 改外观/透明度」的通道（外观生成时定死），
     * 而这批粒子又走加法混合 —— 于是「越靠尾越暗」只能在**生成时**把颜色与 alpha 一起压低，
     * 让短寿命与低亮度同时收掉一颗火花（加法混合下 alpha 就是亮度权重，见 PD `BridgeParticle.getLayer`）。
     */
    private fun hotColor(hot: Float): Color {
        val h = hot.coerceIn(0f, 1f)
        return color.lerp(HOT, (h * COLOR_TOWARD_WHITE).coerceIn(0f, 1f))
            .multiply(DIM_FLOOR + (1f - DIM_FLOOR) * (COLOR_GAIN * h).coerceIn(0f, 1f))
            .withAlpha(ALPHA_FLOOR + (1f - ALPHA_FLOOR) * (ALPHA_GAIN * h).coerceIn(0f, 1f))
    }

    /**
     * **符文菱形**（自定义贴图 `ParticleTextures.RUNE_SPARK`）：本模组拖尾的统一形状。
     *
     * 尺寸口径：贴图 16×16 ⇒ `texScale = 1` ⇒ `anisoWorld(w, w)` 的 `w` **就是世界格整宽**；
     * 菱形只占贴图的 14/16，所以「看着的直径 ≈ 0.85 × [blocks]」。
     *
     * 加色 + 全亮：亮部叠亮做出"魔法发光"，而形状本身是**硬边轮廓**（不是柔边圆点），
     * 所以轮廓清楚 —— 这正是用户第 45 轮要的「清晰的轮廓 + MC 魔法感」。
     */
    private fun runeVisual(blocks: Double): ParticleVisual =
        ParticleVisual().texture(ParticleTextures.RUNE_SPARK)
            .anisoWorld(blocks.toFloat(), blocks.toFloat())
            .glowing(true)
            .additive(true)

    // =====================================================================================
    // 确定性随机
    // =====================================================================================

    /** 与 [dir] 垂直的一个稳定单位向量（侧向散开用；方向退化时给 +X 兜底）。 */
    private fun perpendicular(dir: Vec3): Vec3 {
        val ref = if (abs(dir.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val p = dir.cross(ref)
        return if (p.lengthSqr() < 1e-9) Vec3(1.0, 0.0, 0.0) else p.normalize()
    }

    /** 单位球面上的一点：按黄金角摊开方位角、哈希决定纬度（不用 `Random`，可复现）。 */
    private fun spherePoint(index: Int, salt: Int): Vec3 {
        val theta = GOLDEN_ANGLE * index + hash01(seed, status.age + index + salt) * TAU
        val z = 2.0 * hash01(seed, status.age + index + salt + 20) - 1.0
        val r = sqrt(max(0.0, 1.0 - z * z))
        return Vec3(r * cos(theta), z, r * sin(theta))
    }

    /** 整数哈希取 [0,1) 浮点（xorshift 混淆，两个输入各占一维）。 */
    private fun hash01(a: Int, b: Int): Double {
        var h = a * 0x9E3779B1.toInt()
        h = h xor (b * 0x85EBCA6B.toInt())
        h = h xor (h ushr 15)
        h *= 0x2545F491.toInt()
        h = h xor (h ushr 13)
        return (h ushr 8).toDouble() / (1 shl 24).toDouble()
    }

    private companion object {
        /** 白热端：核的颜色（主色靠 [hotColor] 混入） */
        val HOT: Color = Color.ofInt(255, 250, 235)

        /** 尾部压暗下限：让尾端明显比头部暗，但不会黑成脏点 */
        const val DIM_FLOOR = 0.45f

        /** 尾部透明度下限：加法混合下 alpha 就是亮度权重（PD 没有衰减通道） */
        const val ALPHA_FLOOR = 0.35f

        /** `hot` → 亮度 / alpha 的增益（把中低段拉起来，见 [hotColor]） */
        const val COLOR_GAIN = 1.5f
        const val ALPHA_GAIN = 1.5f

        /**
         * `hot` → 白热程度的强度。**余痕是主体**，所以别调大：调大整条尾迹会褪成白黄、丢掉主色。
         * 白热只该出现在核（`hot=1`）与余痕最前那一两颗（`hot≈0.55`）。
         */
        const val COLOR_TOWARD_WHITE = 0.75f

        /** 视为静止的速度阈值（blocks/tick）；到这个速度算「跑满」 */
        const val MIN_SPEED = 0.02
        const val SPEED_FOR_FULL = 1.2

        const val TAU = 2.0 * PI
        const val GOLDEN_ANGLE = 2.399963229728653

        // ---- ② 白热核（本体上的那一颗） ----
        const val CORE_SIZE = 0.13
        const val CORE_LIFETIME = 3

        // ---- ① 扫过的余痕（尾迹主体：沿轨迹排开、逐渐变小变淡的符文菱形） ----
        /** 每 tick 铺几颗（沿「上一帧 → 本帧」均分） */
        const val SMEAR_COUNT = 5

        /** 尺寸（格）：贴着本体的最大 → 最远的最小 */
        const val SMEAR_SIZE_MAX = 0.38
        const val SMEAR_SIZE_MIN = 0.16

        /** `hot`：贴着本体的最亮（0.55）→ 最远的几乎只剩主色（0.08） */
        const val SMEAR_HOT_NEAR = 0.55
        const val SMEAR_HOT_FAR = 0.08

        /** 侧向抖动（格）：给"手划过的痕迹"一点不齐，但不是随机点云 */
        const val SMEAR_JITTER = 0.045

        /**
         * 寿命（tick）：本体每 tick 都补新的、旧的到点消失 ⇒ 尾迹长度 ≈ 速度 × 本值
         * （火球 0.5 格/tick × 8 ≈ **4 格**）。
         *
         * ⚠️ 这个值同时决定**退场曲线的坡度**：本体死亡后余痕不再补充，`寿命` 就是整条尾迹
         * 散尽所需的 tick 数。给 6 时退场判据量到 `50%→5%` 只跨 **3 帧**（要求 ≥4）——
         * 第 46 轮一度调到 8 才过关；第 47 轮用户嫌尾迹长，回到 **7**（尾迹 ≈ 速度 × 7），
         * 退场那 4 帧的余量改由死前补撒承担（[DEATH_SPARKS] / [DEATH_LIFETIME_MAX]）。
         */
        const val SMEAR_LIFETIME = 7

        // ---- ③ 细碎符文（只做质感，不拉长尾迹） ----
        const val MOTE_BASE = 3.5f
        const val MOTE_MAX = 6
        const val MOTE_BACK_MIN = 0.05
        const val MOTE_BACK_MAX = 0.45
        const val MOTE_RADIUS = 0.09
        const val MOTE_SIZE_MIN = 0.045
        const val MOTE_SIZE_MAX = 0.075
        const val MOTE_SPEED_MIN = 0.10
        const val MOTE_SPEED_MAX = 0.22
        const val MOTE_BACK_SPEED = 0.03
        const val MOTE_LIFETIME_MIN = 2
        const val MOTE_LIFETIME_MAX = 4

        /** 恒定下坠力（blocks/tick²）：寿命只有 2~5 tick，一点点"重量感"就够 */
        val FALLING: Vec3 = Vec3(0.0, -0.012, 0.0)
        const val FORCE_FOREVER = -1

        // ---- 死亡补撒（寿命必须错开：同寿 = 同时消失 = 退场成悬崖，见 [onDie]） ----
        const val DEATH_SPARKS = 22
        const val DEATH_SPEED = 0.16
        const val DEATH_LIFETIME_MIN = 3
        const val DEATH_LIFETIME_MAX = 20

        // ---- 静止场的退场余晖（见 [emitFieldAfterglow]：静止场没有尾迹，退场要单独补） ----
        const val AFTERGLOW_DOTS = 26
        const val AFTERGLOW_RADIUS_MIN = 0.25
        const val AFTERGLOW_RADIUS_MAX = 1.20

        /**
         * 尺寸与亮度：**不能比它替掉的那套（飞行式死前补撒）更弱**。
         *
         * 第 47 轮第一次给的是 0.06~0.13 格、`hot` 0.25~0.70 —— 实测把黑洞场景的退场判据
         * 从 5 帧打成 3 帧：黑洞自己的塌缩在某一帧"啪"地结束时，屏幕上得**已经有足够亮的东西**
         * 接住，否则 5% 阈值那一帧就落在断崖上。现在按「≥原补撒的亮度与尺寸」给。
         */
        const val AFTERGLOW_SIZE_MIN = 0.09
        const val AFTERGLOW_SIZE_MAX = 0.20
        const val AFTERGLOW_SPEED = 0.03
        const val AFTERGLOW_LIFETIME_MIN = 6
        const val AFTERGLOW_LIFETIME_MAX = 24

        // ---- ambient（静止场：黑洞 / 重力场 / delayed_cast） ----
        const val AMBIENT_BASE = 2.4f
        const val AMBIENT_MAX = 4

        /** 氛围粒子的半径带（格）：摊在核心外面**一小圈** */
        const val AMBIENT_RADIUS_MIN = 0.10
        const val AMBIENT_RADIUS_MAX = 0.55
        const val AMBIENT_DOT = 0.07
        const val AMBIENT_SPEED = 0.015
        const val AMBIENT_LIFETIME = 6
        const val AMBIENT_MOTE_PERIOD = 5
        const val AMBIENT_MOTE_CHANCE = 0.45
        const val AMBIENT_MOTE_RADIUS = 0.12
        const val AMBIENT_MOTE_LIFETIME = 4
    }
}
