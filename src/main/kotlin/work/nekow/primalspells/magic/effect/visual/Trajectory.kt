package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Anchor
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.CurveKey
import work.nekow.particledrawing.api.EmitterHandle
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleSpawnSpec
import work.nekow.particledrawing.api.ParticleVisual
import work.nekow.particledrawing.core.easing.EasingType
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
 * 拖尾特效（ParticleDrawing **1.0.14 的新模型**）：**头部一颗最大最亮的白热菱形 + 身后一条密集、
 * 逐个收细淡出、末尾消失的符文尾迹**。
 *
 * ## 三层结构（**头部与尾迹故意错开一 tick**）
 *
 * | 层 | PD 原语 | 读数 |
 * |---|---|---|
 * | **① 头部白热菱形** | 单颗 `ParticleHandle` + `track`（永存） | 「第一个最大的」本体标记；**逐渲染帧丝滑前进** |
 * | **② 尾迹** | `ParticleEmitter`（**按里程**发射，锚点比头部晚一 tick） | 密而可数 → 沿来路单向收细收淡 → 末尾消失 |
 * | **③ 细碎符文** | `spawnAll` 一次性撒点（带寿命曲线） | 贴着弹体一闪即散的质感层 |
 *
 * ⚠️ **为什么头部不能用"尾迹最前那一颗"充数**（第 48 轮实机反馈："法术前进的帧率只有大约 20"）：
 * `track` 由客户端按 partialTick 在 `prevPos → pos` 之间插值，与 MC 原版粒子同一节奏 ⇒ 丝滑；
 * 而发射器锚点是「20Hz 采样 + 速度 × partialTick 外推」，拿它当"眼睛跟着的那个点"就会看出 tick 台阶。
 * 反过来，两条通路的相位若不固定，就会出现"小的拖尾在本体前面"与"闪烁"（同一根因的两种表现）。
 * 所以：**头部管丝滑、尾迹管留痕，锚点压到上上 tick 保证尾迹永远在头部之后**（见 [prevPrevPos]）。
 *
 * ## 为什么换成发射器（0.14 之前做不到的两件事）
 *
 * 旧实现是「服务端每 tick 按里程采样、逐颗 `create().spawn()` + `recolor`/`resize` 补间」，
 * 而且因为程序化粒子按绝对位置渲染、本体走 `track` 被 partialTick 插值，只能把采样段**整体延迟
 * 一 tick** 才勉强做到「拖尾在本体后面」。两条根本限制（都读源码核实过）：
 *
 * 1. 旧**运行时通路没有逐帧求值** —— 缓动只在 `onClientTick`（20Hz）推进，非运动粒子还要按
 *    `particleBatchSize` 轮转分批（PD 自己注释"时走时停"）⇒ 出现/淡出都是 tick 量化的、高刷不顺；
 * 2. 插值语义不统一 —— 程序化粒子绝对位置 vs `track` 的 partialTick 插值。
 *
 * 0.14 的发射器把这两条一起解决了：**服务端只声明一次，客户端按渲染帧、用锚点的插值位置推进里程**，
 * 于是「出现/消淡逐帧、带宽 O(1)」，而且锚点与 `track` 是**同一套插值语义**（见 [ensureEmitter]）。
 * 游戏语义仍留在本侧：长度按格给、寿命＝长度÷速度、强度倍率都由这里算好再翻译成参数。
 *
 * ## 静止时（黑洞 / 重力场 / `delayed_cast`，`speed ≈ 0`）
 *
 * 不铺尾迹，改撒**缓漂的余烬 + 偶发白核**（[emitAmbient]）。位置要摊在球壳上 ⇒ 发射器（点发射）
 * 表达不了，走 `spawnAll` 批量生成 + 批量速度。
 *
 * 阶段：[EffectPhase.VISUAL] —— 在位置积分之后执行，读到本 tick 的最终位置与速度。
 *
 * @param color     拖尾**主色**（各法术原本传入的颜色：火球橙、激光青、黑洞紫…）
 * @param intensity 整体倍率（粒子数与尺寸同乘），默认 1；给 0 即完全关闭该法术的尾迹
 * @param length    拖尾长度（**格**，与速度无关；给 0 也等于关闭本效果的尾迹），默认 [TRAIL_BLOCKS]
 */
class Trajectory(
    val color: Color,
    val intensity: Float = 1f,
    val length: Double = TRAIL_BLOCKS,
) : BaseEffect() {
    override val phase = EffectPhase.VISUAL

    /** 本效果的稳定身份（确定性随机的"哪一颗投射物"那一维）。 */
    private var seed: Int = 0

    /**
     * 上一 tick 的权威位置（= 本 tick **动作前**的位置）：头部 `track` 的那一对插值端点里的前一个。
     */
    private var prevPos: Vec3? = null

    /**
     * 再前一 tick 的权威位置。**尾迹发射器锚在它上面**（比头部整整晚一个 tick）。
     *
     * 为什么要故意晚一 tick：头部走 `track`（客户端按 partialTick 在 `prevPos → pos` 之间插值，逐渲染帧、
     * 丝滑），发射器锚点走的是「锚点位置 + 速度 × partialTick」**外推**——两条通路的相位并不严格相等。
     * 把锚点压到"上上 tick"，发射器的位置就恒 ≤ `prevPos` ≤ 头部渲染位置，**任何相位下都不会越过头部**；
     * 同时相位差固定（不再摆动）⇒ 那次"闪烁"也一起消失。
     */
    private var prevPrevPos: Vec3? = null

    /** 头部那颗白热菱形：单颗、永存、`track` 跟走 —— 它就是"法术本体"，也是丝滑前进的那个点。 */
    private var head: ParticleHandle? = null

    /** 尾迹发射器（服务端只维护这份声明；客户端逐渲染帧发射）。 */
    private var emitter: EmitterHandle? = null

    /** 声明发射器时用的速度：速度明显变化就重挂（寿命 = 长度 ÷ 速度，速度变了寿命也得变）。 */
    private var emitterSpeed: Double = 0.0

    override fun onActive() {
        seed = System.identityHashCode(this)
        prevPos = null
        prevPrevPos = null
        head = null
    }

    override fun onTick() {
        val level = caster.level() as? ServerLevel ?: return
        val manager = wand.pdParticles(level)
        val pos = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        val velocity = Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble())
        // 三个位置：pos = 本 tick 权威位置；anchorPos = 本 tick 动作前的位置（头部 track 的插值区间起点）；
        // lagAnchor = 再前一 tick 的位置（尾迹发射器的锚点，见 [prevPrevPos]）
        val anchorPos = prevPos ?: pos
        val lagAnchor = prevPrevPos ?: anchorPos
        prevPrevPos = anchorPos
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

        // ① 头部白热菱形：单颗、永存、`track` —— 丝滑前进的"本体"
        ensureHead(manager, pos)
        // ② 尾迹：发射器（锚点比头部晚一 tick ⇒ 永不越到前面）
        ensureEmitter(manager, lagAnchor, velocity, speed, strength)
        // ③ 贴着弹体、快速向附近散开又很快消失的细小符文（只做质感，不做长度）
        emitMotes(manager, anchorPos, dir, side, up, strength)
    }

    /**
     * 释放完毕就地补一小撮余烬：让「法术消失」也有一拍停顿，而不是粒子说没就没。
     *
     * ⚠️ 寿命**必须错开**（[DEATH_LIFETIME_MIN]..[DEATH_LIFETIME_MAX]）：早先给的是同一个常数，
     * 结果整批火花在同一 tick 到点、全局曲线是个**悬崖** —— 实机录制的退场判据
     * （`50% → 5%` 的跨度 ≥4 帧）当场把它抓出来了。错开之后是逐颗收掉的**渐隐**。
     *
     * 0.14 起整批走 `spawnAll`（一次包）+ 批量速度/力（各一次包），不再逐颗下发。
     */
    override fun onDie() {
        head?.remove()
        head = null
        emitter?.stop()
        emitter = null
        emitterSpeed = 0.0
        val level = caster.level() as? ServerLevel ?: return
        val manager = wand.pdParticles(level)
        val pos = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        val velocity = Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble())
        val speed = velocity.length()
        val dir = if (speed > MIN_SPEED) velocity.scale(1.0 / speed) else Vec3(0.0, 1.0, 0.0)
        val side = perpendicular(dir)
        val up = dir.cross(side).normalize()
        prevPos = null
        prevPrevPos = null
        if (speed < MIN_SPEED) {
            emitFieldAfterglow(manager, pos, side, up)
            return
        }
        val specs = ArrayList<ParticleSpawnSpec>(DEATH_SPARKS)
        val velocities = ArrayList<Vec3>(DEATH_SPARKS)
        repeat(DEATH_SPARKS) { i ->
            val theta = GOLDEN_ANGLE * i + hash01(seed, i + 401) * TAU
            val outward = side.scale(cos(theta)).add(up.scale(sin(theta)))
            velocities += outward.normalize().scale(DEATH_SPEED * (0.5 + hash01(seed, i + 421)))
                .add(dir.scale(-DEATH_SPEED * 0.4))
            val lifetime = DEATH_LIFETIME_MIN +
                (hash01(seed, i + 481) * (DEATH_LIFETIME_MAX - DEATH_LIFETIME_MIN)).toInt()
            val size = MOTE_SIZE_MIN + (MOTE_SIZE_MAX - MOTE_SIZE_MIN) * hash01(seed, i + 461)
            specs += ParticleSpawnSpec().position(pos)
                .color(hotColor(0.55f + 0.45f * hash01(seed, i + 441).toFloat()))
                .visual(runeVisual(size * 1.6))
                .lifetime(lifetime)
                .fadeOut(tailFade(lifetime), EasingType.LINEAR)
        }
        launch(manager, specs, velocities, FALLING)
    }

    // =====================================================================================
    // ① 尾迹发射器
    // =====================================================================================

    /**
     * ① **头部白热菱形**：单颗、永存、每 tick `track` 到权威位置。
     *
     * 为什么必须是 `track`（而不是让尾迹最前那颗充当头部）：`track` 由客户端按 **partialTick 在
     * `prevPos → pos` 之间插值**，与 MC 原版粒子同一套渲染节奏 ⇒ **逐渲染帧、丝滑前进**；而发射器的
     * 锚点是 20Hz 采样 + 速度外推，用它当"头"就会看出 20Hz 台阶（第 48 轮实机反馈："法术前进的帧率
     * 只有大约 20"）。头部负责"丝滑 + 标记本体"，尾迹负责"密而渐隐的痕"，两者用 [prevPrevPos] 的
     * 一 tick 错位保证永不互相穿插。
     */
    private fun ensureHead(manager: ParticleManager, pos: Vec3) {
        if (intensity <= 0f) return
        if (head == null) {
            head = manager.create()
                .position(pos.x, pos.y, pos.z)
                .color(hotColor(1f))          // 纯白热
                .visual(runeVisual(HEAD_BLOCKS)) // 0.60 格（世界格口径）= 与尾迹出生那颗同尺寸 ⇒ 接上不留台阶
                .lifetime(-1)                 // 永存，由 [onDie] 移除
                .spawn()
        }
        head?.track(pos.x, pos.y, pos.z)
    }

    /**
     * ② **尾迹发射器**。
     *
     * 发给 PD 的是一句声明（「沿这个锚点、每 [TRAIL_SPACING] 格一颗、活 life tick、出生 0.50 格、
     * 沿寿命收细淡出」），**发射发生在客户端、按渲染帧、用锚点的插值位置推进里程**。
     *
     * ⚠️ 锚点给的是 **`lagAnchor` = 上上 tick 的位置**（见 [prevPrevPos]）：比头部整整晚一 tick，
     * 于是任何 partialTick 相位下都不会有尾迹粒子冒到本体前面（实机反馈过的"小的拖尾出现在最大的
     * 本体前面"与"闪烁"都源于两套时钟的相位差）。
     *
     * 寿命只在速度明显变化时重挂（[SPEED_REBUILD_RATIO]）：`life = 长度 ÷ 速度 ÷ 可见占比`，
     * 而 `EmitterHandle` 不能改寿命，所以速度漂了就只能重新声明一次（旧粒子各自走完寿命，不受影响）。
     */
    private fun ensureEmitter(
        manager: ParticleManager,
        lagAnchor: Vec3,
        velocity: Vec3,
        speed: Double,
        strength: Float,
    ) {
        if (intensity <= 0f || strength <= 0f || length <= 0.0) return
        // 寿命 = (length ÷ 速度) ÷ 可见占比：**length 是"肉眼可见的那一段"的格数**。
        // 末段约 15% 寿命是淡出区（设计要求"末尾完全消失"），实测"可见/名义 ≈ 0.85"（第 48 轮）。
        val life = (length / speed / TRAIL_VISIBLE_FRACTION).roundToInt()
            .coerceIn(TRAIL_LIFE_MIN, TRAIL_LIFE_MAX)
        val anchor = Anchor.Movable(lagAnchor, velocity)
        val current = emitter
        if (current != null && abs(speed - emitterSpeed) <= emitterSpeed * SPEED_REBUILD_RATIO) {
            current.updateAnchor(anchor) // 每 tick 挪锚点（PD 侧只在变化时发包）
            return
        }
        current?.stop()
        val headHot = (TRAIL_HOT_HEAD_BASE + (1f - TRAIL_HOT_HEAD_BASE) * strength).coerceIn(0f, 1f)
        emitter = manager.emitter(anchor)
            .spacing(TRAIL_SPACING)                                   // 密：每 0.15 格一颗
            .life(life)
            .color(hotColor(headHot).withAlpha(TRAIL_ALPHA))          // 出生即白热；alpha 压低见 [TRAIL_ALPHA]
            .scale(TRAIL_SCALE_HEAD)                                  // 出生 0.50 格（头部 0.60，略小一些）
            .texture(ParticleTextures.RUNE_SPARK)
            .glowing(true)
            .additive(true)
            // 尺寸乘数：从**本体大小**（1.0 = 0.60 格）沿寿命**线性**收到 0.17（= 0.10 格）。
            // ⚠️ 别在前段加陡降（曾经用过「前 1/3 寿命掉到 55%」）：那会在本体与尾迹之间留一道
            // 尺寸台阶，实机反馈「本体明显比后面的拖尾要大，要之前的连续感」。要连续就线性、从同一尺寸起铺。
            .sizeCurve(
                CurveKey.at(0, 1f),
                CurveKey.at(life, TRAIL_SCALE_TAIL / TRAIL_SCALE_HEAD),
            )
            // 透明度乘数：前 8 成寿命几乎不淡、末段迅速化掉——"逐渐变淡、末尾消失"
            .alphaCurve(
                CurveKey.at(0, 1f),
                CurveKey.at(life, 0f, EasingType.EASE_IN_CUBIC),
            )
            .maxAlive(TRAIL_MAX_ALIVE)
            .spawn()
        emitterSpeed = speed
    }

    // =====================================================================================
    // ③ 细碎符文 / 静止场 / 死亡补撒：一次性撒点，批量下发
    // =====================================================================================

    /**
     * ③ **细碎符文**：贴着弹体、快速向附近散开、很快消失的小菱形（只做质感，不做长度）。
     *
     * 尺寸 0.05~0.09 格、速度 0.10~0.22 格/tick、寿命 2~5 tick ⇒ `v·t` 只有 0.2~1.0 格。
     * 出生带压在本体后方 0.05~0.45 格，不参与"拉长尾迹"（那是 [ensureEmitter] 的活）。
     *
     * 位置要逐颗抖开 ⇒ 发射器（点发射）表达不了，走 `spawnAll`（一次包）+ 批量速度/力（各一次包）；
     * 淡出改用寿命曲线（`fadeOut`），不再逐颗发更新包。
     */
    private fun emitMotes(
        manager: ParticleManager,
        base: Vec3,
        dir: Vec3,
        side: Vec3,
        up: Vec3,
        strength: Float,
    ) {
        if (intensity <= 0f || strength <= 0f) return
        val count = (MOTE_BASE * intensity * (0.6f + 0.4f * strength)).roundToInt().coerceIn(1, MOTE_MAX)
        val specs = ArrayList<ParticleSpawnSpec>(count)
        val velocities = ArrayList<Vec3>(count)
        repeat(count) { i ->
            val back = MOTE_BACK_MIN + (MOTE_BACK_MAX - MOTE_BACK_MIN) * hash01(seed, status.age + i + 91)
            val along = base.subtract(dir.scale(back))
            val theta = GOLDEN_ANGLE * i + hash01(seed, status.age + i + 111) * TAU
            val radius = MOTE_RADIUS * sqrt(hash01(seed, status.age + i + 131))
            val offset = side.scale(radius * cos(theta)).add(up.scale(radius * sin(theta)))
            val outward = if (offset.lengthSqr() < 1e-9) side else offset.normalize()
            val speed = MOTE_SPEED_MIN + (MOTE_SPEED_MAX - MOTE_SPEED_MIN) * hash01(seed, status.age + i + 151)
            velocities += outward.scale(speed).add(dir.scale(-MOTE_BACK_SPEED))
            val size = MOTE_SIZE_MIN + (MOTE_SIZE_MAX - MOTE_SIZE_MIN) * hash01(seed, status.age + i + 211)
            // 亮度压到 0.18~0.62：火花是「余光」，不能抢过余痕与本体的对比（用户口径「飞出去的小粒子太扎眼」）
            val hot = 0.18f + 0.44f * hash01(seed, status.age + i + 191).toFloat()
            val life = MOTE_LIFETIME_MIN +
                (hash01(seed, status.age + i + 231) * (MOTE_LIFETIME_MAX - MOTE_LIFETIME_MIN)).toInt()
            specs += ParticleSpawnSpec().position(along.add(offset))
                .color(hotColor(hot * strength))
                .visual(runeVisual(size))
                .lifetime(life)
                .fadeOut(life, EasingType.LINEAR)   // 整段寿命均匀淡出（等价于旧的 recolor 到 alpha 0）
        }
        launch(manager, specs, velocities, FALLING)
    }

    /**
     * **静止场的退场余晖**（黑洞 / 重力场这类 `speed = 0` 的投射物）。
     *
     * 为什么必须单独有一条：飞行的法术有尾迹，本体一死，尾迹在寿命里逐颗淡出 ⇒ 退场天然渐隐；
     * 而**静止场没有尾迹**，粒子全靠 [emitAmbient] 每 tick 现撒，本体一死就**断供**，退场判据
     * （`50%→5%`）会落在断崖上（第 47 轮实测只有 3 帧）。这条余晖就是给它补一段"慢慢收掉"。
     *
     * 手法：**寿命错开 + 从小到大、从亮到暗**，位置摊在球壳上、速度朝外很慢 —— 读起来是
     * "场把自己散掉了"，而不是"啪一下关灯"。
     */
    private fun emitFieldAfterglow(manager: ParticleManager, pos: Vec3, side: Vec3, up: Vec3) {
        val forward = side.cross(up).normalize()
        val specs = ArrayList<ParticleSpawnSpec>(AFTERGLOW_DOTS)
        val velocities = ArrayList<Vec3>(AFTERGLOW_DOTS)
        repeat(AFTERGLOW_DOTS) { i ->
            val theta = GOLDEN_ANGLE * i + hash01(seed, i + 811) * TAU
            val z = 2.0 * hash01(seed, i + 821) - 1.0
            val r = sqrt(max(0.0, 1.0 - z * z))
            val outward = side.scale(r * cos(theta)).add(up.scale(r * sin(theta))).add(forward.scale(z))
            velocities += outward.scale(AFTERGLOW_SPEED * (0.4 + 0.9 * hash01(seed, i + 871)))
            val lifetime = AFTERGLOW_LIFETIME_MIN +
                (hash01(seed, i + 841) * (AFTERGLOW_LIFETIME_MAX - AFTERGLOW_LIFETIME_MIN)).toInt()
            val size = AFTERGLOW_SIZE_MIN + (AFTERGLOW_SIZE_MAX - AFTERGLOW_SIZE_MIN) * hash01(seed, i + 851)
            specs += ParticleSpawnSpec()
                .position(pos.add(outward.scale(AFTERGLOW_RADIUS_MIN + (AFTERGLOW_RADIUS_MAX - AFTERGLOW_RADIUS_MIN) * hash01(seed, i + 831))))
                .color(hotColor(0.6f + 0.4f * hash01(seed, i + 861).toFloat()))
                .visual(runeVisual(size))
                .lifetime(lifetime)
                // ⚠️ 只在寿命**最后 1/4** 淡出：第 47 轮的教训是这一层要比它替掉的旧行为**更亮或相当**
                //    （整段均匀淡出会把"接住塌缩"的那几帧压暗，判据反而变差）。
                .fadeOut(tailFade(lifetime), EasingType.LINEAR)
        }
        launch(manager, specs, velocities, null)
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
        val specs = ArrayList<ParticleSpawnSpec>(dust + 1)
        val velocities = ArrayList<Vec3>(dust + 1)
        repeat(dust) { i ->
            val outward = spherePoint(i, 251)
            velocities += outward.scale(AMBIENT_SPEED * (0.4 + 0.9 * hash01(seed, status.age + i + 311)))
            specs += ParticleSpawnSpec()
                .position(pos.add(outward.scale(AMBIENT_RADIUS_MIN + (AMBIENT_RADIUS_MAX - AMBIENT_RADIUS_MIN) * hash01(seed, status.age + i + 291))))
                .color(hotColor(0.35f * intensity))
                .visual(runeVisual(AMBIENT_DOT))
                .lifetime(AMBIENT_LIFETIME)
                .fadeOut(tailFade(AMBIENT_LIFETIME), EasingType.LINEAR)
        }
        if (hash01(seed, status.age / AMBIENT_MOTE_PERIOD + 701) < AMBIENT_MOTE_CHANCE) {
            specs += ParticleSpawnSpec()
                .position(pos.add(spherePoint(status.age, 331).scale(AMBIENT_MOTE_RADIUS)))
                .color(hotColor(0.9f))
                .visual(runeVisual(CORE_SIZE))
                .lifetime(AMBIENT_MOTE_LIFETIME)
                .fadeOut(tailFade(AMBIENT_MOTE_LIFETIME), EasingType.LINEAR)
        }
        launch(manager, specs, velocities, null)
    }

    // =====================================================================================
    // 批量下发与外观
    // =====================================================================================

    /**
     * 一次包生成整批 + 一次包批量速度 + 一次包批量力（[force] 为 null 时省掉力那一步）。
     * 这是 0.14 之前的"逐颗 `create().spawn()?.setVelocity()?.applyForce()`"的替代。
     */
    private fun launch(
        manager: ParticleManager,
        specs: List<ParticleSpawnSpec>,
        velocities: List<Vec3>,
        force: Vec3?,
    ) {
        if (specs.isEmpty()) return
        val handles = manager.spawnAll(specs)
        val batch = ParticleBatch(manager)
        batch.addAll(handles)
        if (velocities.isNotEmpty()) batch.setVelocityAll(velocities)
        if (force != null) batch.applyForceAll(List(specs.size) { force }, FORCE_FOREVER)
    }

    /** 寿命末段的淡出时长（只在最后约 1/4 淡出；太短会"啪一下没"，太长会把这一层整体压暗）。 */
    private fun tailFade(lifetime: Int): Int =
        (lifetime / 4).coerceAtLeast(1).coerceAtMost(lifetime)

    /**
     * 白热 ↔ 主色：`hot` 越大越白（头部），越小越接近法术主色并压暗（尾部）。
     *
     * 亮度只能在生成时定：颜色与 alpha 一起压低，让短寿命与低亮度同时收掉一颗火花
     * （加法混合下 alpha 就是亮度权重，见 PD `BridgeParticle.getLayer`）。
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
     * 菱形只占贴图的 14/16，且外圈有 1px 全透明描边（这就是"密而不叠"里那道分隔缝的来处）。
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

        /** 尾部透明度下限：加法混合下 alpha 就是亮度权重 */
        const val ALPHA_FLOOR = 0.35f

        /** `hot` → 亮度 / alpha 的增益（把中低段拉起来，见 [hotColor]） */
        const val COLOR_GAIN = 1.5f
        const val ALPHA_GAIN = 1.5f

        /**
         * `hot` → 白热程度的强度。**尾迹是主体**，所以别调大：调大整条尾迹会褪成白黄、丢掉主色。
         * 白热只该出现在头部那颗（`hot=1`）与尾迹出生那一两颗。
         */
        const val COLOR_TOWARD_WHITE = 0.75f

        /** 视为静止的速度阈值（blocks/tick）；到这个速度算「跑满」 */
        const val MIN_SPEED = 0.02
        const val SPEED_FOR_FULL = 1.2

        const val TAU = 2.0 * PI
        const val GOLDEN_ANGLE = 2.399963229728653

        /** 静止场 ambient 里那颗「偶发白热核」的尺寸（格） */
        const val CORE_SIZE = 0.13

        // ---- ① 尾迹发射器 ----
        /**
         * 元素间距（格）：第 48 轮用户口径「要的是**密、但不叠** —— 菱形很多、不会因为重叠而增加
         * 不透明度，外侧一圈透明描边把它们分开」。0.15 格 ≈ 7 颗/格。
         */
        const val TRAIL_SPACING = 0.15

        /**
         * 拖尾长度（格）的**默认值**（= Trajectory 构造函数 `length` 的缺省）：
         * 口径是**肉眼可见的那一段长度**（不是"元素寿命 × 速度"的名义长度 —— 后者要除以
         * [TRAIL_VISIBLE_FRACTION]）。与速度无关。
         */
        const val TRAIL_BLOCKS = 3.5

        /**
         * 可见占比：寿命后段是淡出区，肉眼看不到。实测（第 48 轮，EASE_IN_CUBIC 淡出）≈0.85，
         * 所以元素寿命按 `1 / 本值` 放宽，让**可见长度 ≈ [length]**。
         */
        const val TRAIL_VISIBLE_FRACTION = 0.85
        const val TRAIL_LIFE_MIN = 4
        const val TRAIL_LIFE_MAX = 40

        /** 速度相对变化超过这个比例就重挂发射器（寿命 = 长度 ÷ 速度） */
        const val SPEED_REBUILD_RATIO = 0.20

        /**
         * 尾迹出生 / 末端尺寸（**scalar**，编辑器单位）：贴图 16×16 ⇒ texScale = 1 ⇒ 渲染整宽 = `scale × 0.2` 格，
         * 所以 0.60 格 = 3.0、0.10 格 = 0.5。
         *
         * 出生尺寸 = **头部那颗的大小**（都是 0.60 格）：用户口径「本体明显比后面的拖尾要大」⇒ 要的是
         * **连续感**，所以从本体大小起铺、只做温和递减，中间不留尺寸台阶。
         * （尾迹锚点比头部晚一 tick ≈ 0.4 格，两颗 0.60 格的菱形半径各 0.3 格 ⇒ 正好盖住这点错位，看不出缝。）
         */
        const val TRAIL_SCALE_HEAD = 3.0f
        const val TRAIL_SCALE_TAIL = 0.5f

        /** 头部白热菱形（[runeVisual] 的**世界格**口径）：0.60 格，比尾迹出生那颗大 ⇒「头部最大」 */
        const val HEAD_BLOCKS = 0.60

        /** 头部白热菱形（[runeVisual] 的**世界格**口径）：0.60 格 —— 与尾迹出生那颗**同尺寸**（连续感） */

        /**
         * 出生时的固定 alpha（加色混合下 alpha 就是亮度权重）。
         *
         * 「密而不叠」的关键：0.15 格间距下相邻菱形在几何上必然接触，若每颗都满 alpha，加色叠加会把
         * 近头那一段堆成一条白带。压到 0.62 后两颗重叠处约 1.2×（而不 2.0×），配上 `rune_spark.png`
         * 外圈那 1px 全透明描边 + 柔边，相邻菱形就各自可辨。
         */
        const val TRAIL_ALPHA = 0.62f

        /** 单个发射器的存活上限（防间距给得太小把客户端灌满） */
        const val TRAIL_MAX_ALIVE = 512

        /**
         * 出生时的白热档位（再乘 strength）：**头部那颗要是整条尾迹里最亮最白的**（用户口径「白热菱形」）。
         */
        const val TRAIL_HOT_HEAD_BASE = 0.85f

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

        /** 恒定下坠力（blocks/tick²）：寿命只有几 tick，一点点"重量感"就够 */
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
