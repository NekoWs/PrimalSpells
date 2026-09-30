package work.nekow.primalspells.client.capture

import net.minecraft.world.entity.Entity
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.wand.Wand
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 相机位姿：位置 + 朝向（MC 约定：yaw 0 = 朝 +Z，pitch 正 = 低头）。 */
data class Pose(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)

/** 一条录制期断言：渲染进日志与 `meta.tsv`，由 `assemble.py` 收口判 PASS/FAIL。 */
class Check(
    private val scene: String,
    private val tick: Int,
    private val name: String,
    val ok: Boolean,
    private val detail: String,
) {
    fun render(): String = "$scene\tt=$tick\t$name\tok=$ok\t$detail"
}

/**
 * 一个可录制的实机场景。
 *
 * 时间轴：`[ -warmupTicks, 0 )` 预热（布置好一切但不录）→ `[0, lengthTicks)` 正式录制（每 tick 一帧）。
 *
 * @param moveCamera true = 相机（= 服务端玩家）跟着 [camera] 走；false = 玩家站位不动，只改朝向
 *                   （第三人称「环绕看护盾」用这个）
 */
class Scene(
    val name: String,
    val title: String,
    val warmupTicks: Int,
    val lengthTicks: Int,
    val thirdPerson: Boolean = false,
    val moveCamera: Boolean = true,
    val fov: Int = 70,
    val prepare: (CaptureDriver.SceneCtx) -> Unit,
    val camera: (CaptureDriver.SceneCtx, Int) -> Pose,
    val start: (CaptureDriver.SceneCtx) -> Unit = {},
    val tick: (CaptureDriver.SceneCtx, Int) -> Unit = { _, _ -> },
    val finish: (CaptureDriver.SceneCtx) -> Unit = {},
    val checksAt: (CaptureDriver.SceneCtx, Int) -> List<Check> = { _, _ -> emptyList() },
) {
    val dir: File get() = File(outRoot, name)
    val framesDir: File get() = File(dir, "frames")

    private companion object {
        val outRoot: File
            get() = File(System.getProperty(CaptureDriver.PROP_OUT) ?: "build/capture/out")
    }
}

/**
 * 内置场景清单（第 17 轮：从「人肉进游戏按 F2」变成「一条命令产出实机动画」）。
 *
 * 三个场景各对应一个第 15/16 轮重做过的特效，机位与时长都围绕**验收点**设计：
 *
 * | 场景 | 要看什么 | 关键时间点 |
 * |---|---|---|
 * | `black_hole` | 整壳从中心长出 / 整壳向心塌缩 | 进场 14t、t=70 触发死亡、退场 14t |
 * | `gravity_field` | 球形范围可辨 + 下坠粒子 + 地面波环 | 全程 |
 * | `energy_shield` | 固定球体逐块搭起 / 逐块消散、五/六边形可辨 | 构建 16t、t=100 起消散 |
 *
 * 布景统一是**黑底摄影棚**（`SceneCtx.buildStudio`）：把地面与四壁换成黑色混凝土，
 * 让细线粒子有对比度；这是取景环境，不是玩法场景，`verify.md` 的实机验收仍然按真实游戏环境跑。
 */
object CaptureScenes {

    /** 场景中心（水平）：世界出生点附近，超平坦世界里是确定的一块地。 */
    private const val CX = 0
    private const val CZ = 0

    /** 球心离地高度：黑洞/重力场的作用半径是 5，正好让球底贴着地面。 */
    private const val FIELD_CENTER_LIFT = 5.0

    fun all(): List<Scene> = listOf(blackHole(), gravityField(), energyShield())

    // ---------------------------------------------------------------- 黑洞

    /**
     * 黑洞：半径 5 的引力球壳。
     *
     * 第 16 轮把「球壳 + 另一圈向心粒子」改成**球壳本身做径向动画**，所以这个场景的重点是
     * 「进场时整颗球从中心长出来」「退场时整颗球向中心塌缩」两段 —— 相机绕球缓慢公转，
     * 让球形轮廓在三维上可读（正对时是一个圆，看不出是球）。
     */
    private fun blackHole(): Scene = run {
        val name = "black_hole"
        val lengthTicks = 108
        var wand: Wand? = null
        Scene(
            name = name,
            title = "黑洞：整壳从中心长出(14t) → 稳态 → 整壳向心塌缩(14t)",
            warmupTicks = 24,
            lengthTicks = lengthTicks,
            fov = 70,
            camera = { ctx, t -> orbit(ctx, FIELD_CENTER_LIFT, radius = 13.0, lift = 3.0, degPerTick = 4.0, tick = t) },
            prepare = { ctx ->
                val floorY = ctx.floorBlockY(CX, CZ)
                ctx.buildStudio(CX, CZ, floorY)
                ctx.setup(
                    spectator = true,
                    x = CX + 0.5,
                    y = floorY + 1.0,
                    z = CZ + 0.5,
                    yaw = 0f,
                    pitch = 0f,
                )
                wand = Wand("capture-black-hole").also { w ->
                    w.innate += MagicManager.create("black_hole")
                        ?: error("black_hole 未注册")
                    w.status.mana = 1000.0
                    w.load()
                }
                ctx.log("$name prepared: floorY=$floorY")
            },
            start = { ctx ->
                // 施法点 = 球心（黑洞是静止投射物，中心就是施法者眼睛位置）
                val feetY = ctx.floorBlockY(CX, CZ) + 1.0
                ctx.placeForCast(CX + 0.5, feetY + FIELD_CENTER_LIFT - EYE_HEIGHT, CZ + 0.5)
                val ok = wand!!.spell(ctx.player)
                ctx.log("$name cast -> $ok, projectiles=${MagicManager.projectiles.size}")
            },
            tick = { _, t ->
                // t=70 触发正常死亡路径（onDie → 退场动画），而不是删粒子
                if (t == DEATH_TICK) {
                    MagicManager.projectiles.filter { it.id == "black_hole" }.forEach { it.alive = false }
                }
            },
            checksAt = { _, t -> projectileChecks(name, t, "black_hole", lengthTicks - 1) },
        )
    }

    // ---------------------------------------------------------------- 重力场

    /**
     * 重力场：半径 5 的球形范围 + 下坠粒子 + 落地波环。
     *
     * 第 16 轮的验收点是「范围还是要一眼看出是球」：这个场景公转稍快、机位稍远，
     * 让球壳轮廓、内部下坠粒子、地面波环三者在同一帧里都能看到。
     */
    private fun gravityField(): Scene = run {
        val name = "gravity_field"
        val lengthTicks = 130
        var wand: Wand? = null
        Scene(
            name = name,
            title = "重力场：球形范围 + 下坠粒子 + 落地波环",
            warmupTicks = 24,
            lengthTicks = lengthTicks,
            fov = 70,
            camera = { ctx, t -> orbit(ctx, FIELD_CENTER_LIFT, radius = 14.0, lift = 3.5, degPerTick = 3.0, tick = t) },
            prepare = { ctx ->
                val floorY = ctx.floorBlockY(CX, CZ)
                ctx.buildStudio(CX, CZ, floorY)
                ctx.setup(
                    spectator = true,
                    x = CX + 0.5,
                    y = floorY + 1.0,
                    z = CZ + 0.5,
                    yaw = 0f,
                    pitch = 0f,
                )
                wand = Wand("capture-gravity").also { w ->
                    w.innate += MagicManager.create("gravity_field")
                        ?: error("gravity_field 未注册")
                    w.status.mana = 1000.0
                    w.load()
                }
                ctx.log("$name prepared: floorY=$floorY")
            },
            start = { ctx ->
                val feetY = ctx.floorBlockY(CX, CZ) + 1.0
                ctx.placeForCast(CX + 0.5, feetY + FIELD_CENTER_LIFT - EYE_HEIGHT, CZ + 0.5)
                val ok = wand!!.spell(ctx.player)
                ctx.log("$name cast -> $ok, projectiles=${MagicManager.projectiles.size}")
            },
            tick = { _, t ->
                if (t == DEATH_TICK) {
                    MagicManager.projectiles.filter { it.id == "gravity_field" }.forEach { it.alive = false }
                }
            },
            checksAt = { _, t -> projectileChecks(name, t, "gravity_field", lengthTicks - 1) },
        )
    }

    // ---------------------------------------------------------------- 能量盾

    /**
     * 能量盾：截角二十面体球壳（12 五边形 + 20 六边形 / 540 颗粒子），逐块搭起、逐块消散。
     *
     * 用**第三人称**：护盾包的是身体，第一人称只能看到内侧。玩家站位固定、相机绕 yaw 转，
     * 于是球壳像转台一样慢慢转过一圈 —— 五/六边形的边界在移动中最容易被认出来。
     *
     * 被动驱动走真实路径：`Wand.tick(caster, level)`（= `WandItem.inventoryTick` 里的那次调用）。
     * t=100 起改调 `Wand.tick(null, level)`，也就是**放下法杖**：闲置 4 tick 后开始消散。
     */
    private fun energyShield(): Scene = run {
        val name = "energy_shield"
        var wand: Wand? = null
        var shield: EnergyShield? = null
        var holder: TestDummyEntity? = null
        Scene(
            name = name,
            title = "能量盾：固定球体逐块搭起(16t) → 稳态 → 逐块消散（绕球壳公转）",
            warmupTicks = 24,
            lengthTicks = 150,
            thirdPerson = false,
            moveCamera = true,
            fov = 60,
            camera = { ctx, t ->
                orbit(ctx, centerLift = SHIELD_CENTER_LIFT, radius = 7.0, lift = 2.2, degPerTick = 2.5, tick = t)
            },
            prepare = { ctx ->
                val floorY = ctx.floorBlockY(CX, CZ)
                ctx.buildStudio(CX, CZ, floorY)
                ctx.setup(
                    spectator = true,
                    x = CX + 0.5,
                    y = floorY + 1.0,
                    z = CZ + 0.5,
                    yaw = 0f,
                    pitch = 0f,
                )
                holder = ctx.spawnDummy(CX + 0.5, floorY + 1.0, CZ + 0.5)
                val card = MagicManager.create("energy_shield") as EnergyShield
                val w = Wand("capture-shield").also {
                    it.magics += card
                    it.load()
                }
                wand = w
                shield = card
                ctx.log("$name prepared: loaded=${w.loadedMagics.map { it.id }}")
            },
            tick = { ctx, t ->
                val w = wand!!
                if (t < SHIELD_HOLD_TICKS) {
                    // 「拿在手上」：被动每 tick 驱动 → 逐块搭建并保持
                    w.tick(holder!!, ctx.level)
                } else {
                    // 「放下法杖」：闲置分支 → 4 tick 后开始逐块消散
                    w.tick(null, ctx.level)
                }
            },
            finish = { holder?.remove(Entity.RemovalReason.DISCARDED) },
            checksAt = { _, t ->
                val w = wand
                val s = shield
                when {
                    t == 1 -> listOf(
                        Check(name, t, "wand-loaded", w != null && w.loadedMagics.any { it is EnergyShield }, "loadedMagics=${w?.loadedMagics?.size}"),
                    )
                    t == SHIELD_HOLD_TICKS - 5 -> listOf(
                        Check(name, t, "shield-alive", s != null && s.durability > 0.0, "durability=${s?.durability}"),
                    )
                    t == SHIELD_HOLD_TICKS + 20 -> listOf(
                        // 放下之后耐力仍在（收起球壳不代表破盾）
                        Check(name, t, "durability-after-fold", s != null && s.durability > 0.0, "durability=${s?.durability}"),
                    )
                    else -> emptyList()
                }
            },
        )
    }

    // ---------------------------------------------------------------- 公共件

    /** 玩家眼高（1.8 格身高的标准值）：施法时要让眼睛正好落在球心，脚底就得往下让这么多。 */
    private const val EYE_HEIGHT = 1.62

    /** 触发「正常死亡路径」的录制 tick（onDie → 退场动画）。 */
    private const val DEATH_TICK = 70

    /** 护盾被「拿在手上」的 tick 数，之后改走闲置分支触发消散。 */
    private const val SHIELD_HOLD_TICKS = 100

    /** 护盾球心相对持有者脚底的高度（`EnergyShield` 里是 `bbHeight * 0.55`）。 */
    private const val SHIELD_CENTER_LIFT = 1.1

    /** 绕球心的水平公转机位，看向球心。 */
    private fun orbit(
        ctx: CaptureDriver.SceneCtx,
        centerLift: Double,
        radius: Double,
        lift: Double,
        degPerTick: Double,
        tick: Int,
    ): Pose {
        val floorY = ctx.floorBlockY(CX, CZ)
        val cx = CX + 0.5
        val cz = CZ + 0.5
        val cy = floorY + 1.0 + centerLift
        val angle = Math.toRadians(START_DEG + degPerTick * tick)
        val x = cx + cos(angle) * radius
        val z = cz + sin(angle) * radius
        val y = cy + lift
        val dx = cx - x
        val dz = cz - z
        val dy = cy - y
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz)))).toFloat()
        return Pose(x, y, z, yaw, pitch)
    }

    /** 起始方位角：从 -Z 侧起转，第一帧就能看到球（而不是正对墙）。 */
    private const val START_DEG = -90.0

    /**
     * 静止投射物的生命周期断言：`t=1` 应已存在、`DEATH_TICK` 前仍在、录到最后一帧时必须已经清空。
     *
     * 第三条是第 16 轮的重点（「退场要自己收干净」）：最后一帧还有投射物/粒子残留就是回归。
     */
    private fun projectileChecks(scene: String, tick: Int, id: String, endTick: Int): List<Check> {
        val alive = MagicManager.projectiles.count { it.id == id && it.alive }
        val total = MagicManager.projectiles.size
        return when (tick) {
            2 -> listOf(Check(scene, tick, "spawned", alive == 1, "alive=$alive total=$total"))
            DEATH_TICK - 2 -> listOf(Check(scene, tick, "alive-before-death", alive == 1, "alive=$alive"))
            endTick -> listOf(Check(scene, tick, "cleaned-up", total == 0, "total=$total"))
            else -> emptyList()
        }
    }
}
