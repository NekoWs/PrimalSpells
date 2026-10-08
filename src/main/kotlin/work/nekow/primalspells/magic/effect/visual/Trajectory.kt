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

/** 实色方粒拖尾；track 头部与延后一tick的里程发射器分工，PD负责插值、尺寸曲线和寿命。 */
class Trajectory(
    val color: Color,
    val intensity: Float = 1f,
    val length: Double = TRAIL_BLOCKS,
) : BaseEffect() {
    override val phase = EffectPhase.VISUAL

    private var seed: Int = 0

    private var prevPos: Vec3? = null

    private var prevPrevPos: Vec3? = null

    private var head: ParticleHandle? = null

    private var emitter: EmitterHandle? = null

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

        // ① 头部实色方粒：单颗、永存、`track` —— 丝滑前进的"本体"
        ensureHead(manager, pos)
        // ② 尾迹：发射器（锚点比头部晚一 tick ⇒ 永不越到前面）
        ensureEmitter(manager, lagAnchor, velocity, speed, strength)
        // ③ 贴着弹体、快速向附近散开又很快消失的细小符文（只做质感，不做长度）
        emitMotes(manager, anchorPos, dir, side, up, strength)
    }

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
        }
        launch(manager, specs, velocities, FALLING)
    }

    // =====================================================================================
    // ① 尾迹发射器
    // =====================================================================================

    private fun ensureHead(manager: ParticleManager, pos: Vec3) {
        if (intensity <= 0f) return
        if (head == null) {
            head = manager.create()
                .position(pos.x, pos.y, pos.z)
                .color(hotColor(1f))          // 纯白热
                .visual(runeVisual(HEAD_BLOCKS * (1 + 0.1 * projectile.modifiers.values.sum()) * projectile.areaScale)) // 0.3125格实色方粒，与尾迹起始宽度一致
                .lifetime(-1)                 // 永存，由 [onDie] 移除
                .spawn()
        }
        head?.track(pos.x, pos.y, pos.z)
    }

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
            .spacing(TRAIL_SPACING)                                   // 每0.125格一颗
            .life(life)
            .color(hotColor(headHot))          // 固定色阶，alpha始终为1
            .scale(TRAIL_SCALE_HEAD * (1 + 0.1f * projectile.modifiers.values.sum()) * projectile.areaScale.toFloat())                                  // 出生0.3125格
            .glowing(true)
            .additive(false)
            // 尺寸乘数沿寿命线性收缩，世界尺寸从0.3125格降至0.0625格。
            // ⚠️ 别在前段加陡降（曾经用过「前 1/3 寿命掉到 55%」）：那会在本体与尾迹之间留一道
            // 尺寸台阶，实机反馈「本体明显比后面的拖尾要大，要之前的连续感」。要连续就线性、从同一尺寸起铺。
            .sizeCurve(
                CurveKey.at(0, 1f),
                CurveKey.at(life, TRAIL_SCALE_TAIL / TRAIL_SCALE_HEAD),
            )
            // 实色颗粒按寿命缩小；不添加透明度曲线。
            .maxAlive(TRAIL_MAX_ALIVE)
            .spawn()
        emitterSpeed = speed
    }

    // =====================================================================================
    // ③ 细碎符文 / 静止场 / 死亡补撒：一次性撒点，批量下发
    // =====================================================================================

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
        }
        launch(manager, specs, velocities, FALLING)
    }

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
        }
        launch(manager, specs, velocities, null)
    }

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
        }
        if (hash01(seed, status.age / AMBIENT_MOTE_PERIOD + 701) < AMBIENT_MOTE_CHANCE) {
            specs += ParticleSpawnSpec()
                .position(pos.add(spherePoint(status.age, 331).scale(AMBIENT_MOTE_RADIUS)))
                .color(hotColor(0.9f))
                .visual(runeVisual(CORE_SIZE))
                .lifetime(AMBIENT_MOTE_LIFETIME)
        }
        launch(manager, specs, velocities, null)
    }

    // =====================================================================================
    // 批量下发与外观
    // =====================================================================================

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

    private fun hotColor(hot: Float): Color = when {
        status.freezeTicks > 0 -> Color.ofInt(120, 220, 255)
        hot >= 0.98f -> HOT
        hot >= 0.5f -> color.lerp(HOT, 0.25f).withAlpha(1f)
        else -> color.withAlpha(1f)
    }

    private fun runeVisual(blocks: Double): ParticleVisual =
        ParticleVisual().anisoWorld(blocks.toFloat(), blocks.toFloat()).glowing(true).additive(false)

    // =====================================================================================
    // 确定性随机
    // =====================================================================================

    private fun perpendicular(dir: Vec3): Vec3 {
        val ref = if (abs(dir.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val p = dir.cross(ref)
        return if (p.lengthSqr() < 1e-9) Vec3(1.0, 0.0, 0.0) else p.normalize()
    }

    private fun spherePoint(index: Int, salt: Int): Vec3 {
        val theta = GOLDEN_ANGLE * index + hash01(seed, status.age + index + salt) * TAU
        val z = 2.0 * hash01(seed, status.age + index + salt + 20) - 1.0
        val r = sqrt(max(0.0, 1.0 - z * z))
        return Vec3(r * cos(theta), z, r * sin(theta))
    }

    private fun hash01(a: Int, b: Int): Double {
        var h = a * 0x9E3779B1.toInt()
        h = h xor (b * 0x85EBCA6B.toInt())
        h = h xor (h ushr 15)
        h *= 0x2545F491.toInt()
        h = h xor (h ushr 13)
        return (h ushr 8).toDouble() / (1 shl 24).toDouble()
    }

    private companion object {
        val HOT: Color = Color.ofInt(255, 250, 235)

        const val MIN_SPEED = 0.02
        const val SPEED_FOR_FULL = 1.2

        const val TAU = 2.0 * PI
        const val GOLDEN_ANGLE = 2.399963229728653

        const val CORE_SIZE = 0.13

        // ---- ① 尾迹发射器 ----
        const val TRAIL_SPACING = 0.125

        const val TRAIL_BLOCKS = 3.5

        const val TRAIL_VISIBLE_FRACTION = 0.85
        const val TRAIL_LIFE_MIN = 4
        const val TRAIL_LIFE_MAX = 40

        const val SPEED_REBUILD_RATIO = 0.20

        const val TRAIL_SCALE_HEAD = 1.5625f
        const val TRAIL_SCALE_TAIL = 0.3125f

        const val HEAD_BLOCKS = 0.3125

        const val TRAIL_MAX_ALIVE = 512

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

        const val AFTERGLOW_SIZE_MIN = 0.09
        const val AFTERGLOW_SIZE_MAX = 0.20
        const val AFTERGLOW_SPEED = 0.03
        const val AFTERGLOW_LIFETIME_MIN = 6
        const val AFTERGLOW_LIFETIME_MAX = 24

        // ---- ambient（静止场：黑洞 / 重力场 / delayed_cast） ----
        const val AMBIENT_BASE = 2.4f
        const val AMBIENT_MAX = 4

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
