package work.nekow.primalspells.client.capture

import net.minecraft.world.InteractionHand
import net.minecraft.world.item.DyeColor
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.DamageNumber
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager
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

    fun all(): List<Scene> = listOf(blackHole(), gravityField(), energyShield(), implosion())

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
            checksAt = { ctx, t -> projectileChecks(name, t, "black_hole", lengthTicks - 1, ctx) },
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
            checksAt = { ctx, t -> projectileChecks(name, t, "gravity_field", lengthTicks - 1, ctx) },
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
    /**
     * 玻璃护盾（第 23 轮重做）：**平时完全不可见** → 受击处展开一块玻璃板 → 破盾一闪而碎 → 恢复闪一下。
     *
     * 攻击者是**第二个测试木桩**（`caster` 必须不是护盾持有者，否则投射物会被
     * `p.caster === player` 排除），拿一根只装火球、`delay = 0` 的法杖，站在 +X 方向 6.5 格外平射 ——
     * 走真实施法路径，护盾走真实弹开路径。
     *
     * 时间线（tick）：
     * ```
     *  0-19   静止：屏幕上应当**一个粒子都没有**（护盾平时不可见）
     * 22/34/46/58  四次点射：每次格挡成功都在受击处亮起一块白玻璃、约 0.35 秒后消失（四个亮点）
     * 76     大爆（中心摆在壳外 3 格）：抵扣成功 → 扣一大笔 MP，护盾不破
     * 96     同样的大爆，但先把法力压到不够：打穿 → 受击一圈碎裂 + 3 秒淡红破口轮廓
     * 156    破口到期，护盾回到「平时不可见」
     * 168    激光（5 格/tick）：验证「命中兜底」拦得住单 tick 内穿壳贴脸的高速法术
     * ```
     */
    private fun energyShield(): Scene = run {
        val name = "energy_shield"
        var wand: Wand? = null
        var shield: EnergyShield? = null
        var holder: TestDummyEntity? = null
        var shooterWand: Wand? = null
        var attacker: TestDummyEntity? = null
        var manaBeforeBlast = 0.0
        Scene(
            name = name,
            title = "玻璃护盾：平时不可见 → 受击玻璃板 → 大爆抵扣 → 法力不足打穿 + 破口",
            warmupTicks = 24,
            lengthTicks = SHIELD_SCENE_TICKS,
            thirdPerson = false,
            moveCamera = true,
            fov = 60,
            camera = { ctx, t ->
                orbit(ctx, centerLift = SHIELD_CENTER_LIFT, radius = 6.0, lift = 2.0, degPerTick = 2.0, tick = t)
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
                val guardDummy = ctx.spawnDummy(CX + 0.5, floorY + 1.0, CZ + 0.5)
                holder = guardDummy
                // 护盾挂在**真实法杖物品**上：`ShieldGuard` 是按「手上的法杖」找护盾的
                // （爆炸抵扣那条路要从实体反查到护盾），裸 `Wand` 实例它找不到。
                val stack = ItemStack(ModItems.WAND.get())
                val w = WandManager.createWand(stack).let { id -> WandManager[id] }
                val card = MagicManager.create("energy_shield") as EnergyShield
                w.magics += card
                w.load()
                // 测试台设置：满法力 + **关掉回复**（`charge = 0`）。默认 1000/s 的回复会在两次大爆之间
                // 把法力补满，「MP 不足 → 打穿」这条分支永远逼不出来。
                w.status.mana = SHIELD_START_MANA
                w.charge = 0.0
                wand = w
                shield = card
                guardDummy.setItemInHand(InteractionHand.MAIN_HAND, stack)
                // 攻击者：+X 方向 5.5 格，yaw=90 → 朝 -X 正对护盾持有者
                attacker = ctx.spawnDummy(CX + 5.5, floorY + 1.0, CZ + 0.5, yaw = 90f)
                val bolt = MagicManager.create("fireball")!!
                val sw = Wand("capture-shooter").also {
                    it.magics += bolt
                    it.load()
                    it.status.mana = 1000.0
                }
                shooterWand = sw
                ctx.log(
                    "$name prepared: shield=${w.loadedMagics.map { m -> m.id }} " +
                        "shooter=${sw.loadedMagics.map { m -> m.id }} mana=${w.status.mana} " +
                        "resistance=${card.baseResistance + card.resistancePerCard}"
                )
            },
            tick = { ctx, t ->
                val w = wand ?: return@Scene
                val h = holder ?: return@Scene
                val sw = shooterWand ?: return@Scene
                val atk = attacker ?: return@Scene
                w.tick(h, ctx.level)
                // ⚠️ 攻击者的法杖要按**两条路径**各推一次：
                // `tick(caster)` 只跑被动（`Wand.tick` 在该分支里直接 return），
                // 而冷却 `status.delay` / `status.recharge` 的递减只在 `tick(caster = null)` 的闲置分支里 ——
                // 少推一次就只能开出第一发（实测后续全是 `[Delay 1/0]`）。
                // 场景里的法杖没有实体物品、不会被 `WandManager` 收进表里，所以得自己推。
                sw.tick(atk, ctx.level)
                sw.tick(null, ctx.level)
                if (t == SHIELD_SHOT_A || t == SHIELD_SHOT_B || t == SHIELD_SHOT_C || t == SHIELD_SHOT_D) {
                    sw.spell(atk)
                }
                if (t == SHIELD_BLAST_OK || t == SHIELD_BLAST_FAIL) {
                    if (t == SHIELD_BLAST_OK) manaBeforeBlast = w.status.mana
                    blast(ctx, atk, sw)
                }
                // 激光回归（第 26 轮）：高速法术（5 格/tick、maxAge 1）在同一 tick 里就穿壳贴脸，
                // 靠的是 `Hurt` 的命中兜底而不是扫场 —— 从壳外 3 格打进，保证它当 tick 真的贴到木桩
                if (t == SHIELD_LASER_AT) {
                    laser(ctx, atk, sw)
                }
                // 第二次大爆前：把法力压到不够（345 > 300），并收掉残余火球
                // —— 后者的橙色拖尾会污染破口窗口的「暖色像素」判据
                if (t == SHIELD_LOW_MANA_AT) {
                    w.status.mana = SHIELD_LOW_MANA
                    MagicManager.projectiles.forEach { it.alive = false }
                }
            },
            finish = {
                holder?.remove(Entity.RemovalReason.DISCARDED)
                attacker?.remove(Entity.RemovalReason.DISCARDED)
            },
            checksAt = { ctx, t ->
                val w = wand
                val s = shield
                when (t) {
                    1 -> listOf(
                        Check(
                            name, t, "wand-loaded",
                            w != null && s != null && w.loadedMagics.any { it is EnergyShield },
                            "loadedMagics=${w?.loadedMagics?.size}",
                        ),
                    )
                    SHIELD_IDLE_END - 1 -> listOf(
                        Check(
                            name, t, "idle-invisible",
                            s != null && !s.breached && w != null && w.status.mana >= SHIELD_START_MANA - 1.0,
                            "breached=${s?.breached} mana=${w?.status?.mana}",
                        ),
                    )
                    SHIELD_SHOT_D + 4 -> listOf(
                        // 四次点射都落在抗性之外一点点：每次 10 MP（基础 5 + 超出 1 点 × 5）
                        Check(
                            name, t, "hits-blocked",
                            s != null && !s.breached && w != null && w.status.mana <= SHIELD_START_MANA - 30.0,
                            "breached=${s?.breached} mana=${w?.status?.mana}",
                        ),
                    )
                    SHIELD_BLAST_OK + SHIELD_BLAST_SETTLE -> listOf(
                        Check(
                            name, t, "blast-absorbed",
                            s != null && !s.breached && w != null && w.status.mana <= manaBeforeBlast - 300.0,
                            "breached=${s?.breached} mana=${w?.status?.mana} before=$manaBeforeBlast",
                        ),
                    )
                    SHIELD_BLAST_FAIL + SHIELD_BLAST_SETTLE -> listOf(
                        // 打穿的两个证据：破口生效 + 伤害数字出现（挡下时一个都不会有）
                        Check(
                            name, t, "breached-and-hit",
                            s != null && s.breached && w != null &&
                                w.status.mana >= SHIELD_LOW_MANA - 0.5 && damageNumbers(ctx, holder) > 0,
                            "breached=${s?.breached} mana=${w?.status?.mana} 伤害数字=${damageNumbers(ctx, holder)}",
                        ),
                    )
                    SHIELD_LASER_AT + 4 -> listOf(
                        // 挡下即扣费：激光强度 0.5 ≤ 抗性 4 → 只付基础 5 MP（300 → 295）。
                        // 这一条如果失败（mana 仍为 300），说明高速法术整个漏过了护盾。
                        Check(
                            name, t, "laser-blocked",
                            w != null && w.status.mana <= SHIELD_LOW_MANA - 4.5,
                            "mana=${w?.status?.mana}（挡下后应 ≈ ${SHIELD_LOW_MANA - 5}）",
                        ),
                    )
                    SHIELD_SCENE_TICKS - 3 -> listOf(
                        Check(name, t, "breach-expired", s != null && !s.breached, "breached=${s?.breached}"),
                        pdParticleCheck(name, t, ctx),
                    )
                    else -> emptyList()
                }
            },
        )
    }

    /**
     * 摆一颗大爆并当帧引爆。
     *
     * `large_explosion` 是 immobile 投射物（`speed = 0` / `maxAge = 1`）：出生点就是爆点，
     * 所以直接把它摆在壳外 3 格 —— 衰减后伤害 = 24 × (1 − 3/6) = 12，强度 = 12 × 半径 6 = 72。
     * 抗性 4（1 张卡）时价钱 = 5 + (72 − 4) × 5 = 345 MP：第一次付得起、第二次（法力 300）付不起。
     */
    private fun blast(ctx: CaptureDriver.SceneCtx, caster: TestDummyEntity, wand: Wand) {
        val floorY = ctx.floorBlockY(CX, CZ)
        val projectile = MagicManager.create("large_explosion") as Projectile
        projectile.caster = caster
        projectile.wand = wand
        projectile.position = Vector3f(CX + 3.5f, floorY + 2.0f, CZ + 0.5f)
        projectile.velocity = Vector3f(0f, 0f, 0f)
        projectile.spell()
        MagicManager.add(projectile)
    }

    /** 场上的伤害数字实体数量：护盾挡下时一个都不会有，打穿时才出现（「真的打到了」的直接证据）。 */
    private fun damageNumbers(ctx: CaptureDriver.SceneCtx, holder: TestDummyEntity?): Int {
        val h = holder ?: return 0
        return ctx.level.getEntitiesOfClass(DamageNumber::class.java, h.boundingBox.inflate(8.0)).size
    }

    /**
     * 从壳外 3 格朝持有者打一发 `laser`（5 格/tick、`maxAge = 1`）。
     *
     * 这一发的意义是**回归「命中兜底」**：它在同一个 tick 里就穿壳贴到木桩身上
     * （`Move` 子步进里 `status.pos = checkPos` 后立刻 `hitEntity`），而护盾的 tick 末尾扫场
     * 在同一 tick 更晚才跑、那时它已经被 `maxAge` 收走 —— 只有 `Hurt` 里的
     * `ShieldGuard.absorbProjectile` 拦得住。断言看的是「法力被扣了 5」（挡下即扣费）。
     */
    private fun laser(ctx: CaptureDriver.SceneCtx, caster: TestDummyEntity, wand: Wand) {
        val floorY = ctx.floorBlockY(CX, CZ)
        val from = Vector3f(CX + 3.0f, floorY + 2.0f, CZ + 0.5f)
        val aim = Vector3f(CX + 0.5f, floorY + 2.0f, CZ + 0.5f)
        val projectile = MagicManager.create("laser") as Projectile
        projectile.caster = caster
        projectile.wand = wand
        projectile.position = from
        projectile.velocity = aim.sub(from).normalize()
        projectile.spell()
        MagicManager.add(projectile)
    }

    private const val DEATH_TICK = 70

    /** 玩家眼睛相对脚底的高度：施法点与机位都按它算 */
    private const val EYE_HEIGHT = 1.62

    private const val SHIELD_CENTER_LIFT = 1.1


    /**
     * **内爆**：定点爆炸的蓄力（黑色核心球 + 蓄力线条 + 向心聚拢粒子）→ 爆发（球体放大到半径 + 向外飞散）。
     *
     * 与前三个场景不同，这里**故意用中灰摄影棚**：蓄力段的核心球是**黑色**的，在原来的黑底棚里根本看不见；
     * 中灰（r≈g≈b）既不参与「暖色亮像素」判据、也不参与蓝判据，却能让黑球成为可判定的暗区。
     *
     * 时间线（tick）：`20` 放一颗 `large_explosion`（半径 6 / 蓄力 18 tick / 0.9 s）→ `38` 爆发 → `78` 结束。
     * 场心放一个木桩：蓄力期间**不该**出现伤害数字（伤害推迟到爆发），爆发后才该出现 —— 这是
     * 「蓄力结束才结算」的直接证据。
     */
    private fun implosion(): Scene = run {
        val name = "implosion"
        var holder: TestDummyEntity? = null
        Scene(
            name = name,
            title = "内爆：黑色核心 + 蓄力线条 + 向心聚拢 → 爆发球放大到半径",
            warmupTicks = 24,
            lengthTicks = IMPLOSION_TICKS,
            thirdPerson = false,
            moveCamera = true,
            fov = 60,
            camera = { ctx, t ->
                orbit(ctx, centerLift = IMPLOSION_CENTER_LIFT, radius = 12.0, lift = 3.0, degPerTick = 1.0, tick = t)
            },
            prepare = { ctx ->
                val floorY = ctx.floorBlockY(CX, CZ)
                ctx.buildStudio(CX, CZ, floorY, color = DyeColor.LIGHT_GRAY)
                ctx.setup(
                    spectator = true,
                    x = CX + 0.5,
                    y = floorY + 1.0,
                    z = CZ + 0.5,
                    yaw = 0f,
                    pitch = 0f,
                )
                holder = ctx.spawnDummy(CX + 0.5, floorY + 1.0, CZ + 0.5)
                ctx.log("$name prepared: radius=$IMPLOSION_RADIUS charge=$IMPLOSION_CHARGE_TICKS")
            },
            tick = { ctx, t ->
                if (t != IMPLOSION_CAST) return@Scene
                val guard = holder ?: return@Scene
                val floorY = ctx.floorBlockY(CX, CZ)
                val bolt = MagicManager.create("large_explosion") as Projectile
                // ⚠️ 施法者必须是**别人**：applyDamageAndKnockback 会排除 source 实体，
                // 用木桩自己当施法者 → 爆炸永远打不到它（诊断时踩到）
                bolt.caster = ctx.player
                bolt.wand = Wand("capture-implosion")
                bolt.position = Vector3f(CX + 0.5f, floorY + 2.0f, CZ + 0.5f)
                bolt.velocity = Vector3f(0f, 0f, 0f)
                bolt.spell()
                MagicManager.add(bolt)
            },
            finish = {
                holder?.remove(Entity.RemovalReason.DISCARDED)
            },
            checksAt = { ctx, t ->
                val particles = runCatching {
                    ParticleManager.of(ctx.level).getEngine().particleCount()
                }.getOrDefault(-1)
                when (t) {
                    IMPLOSION_CAST + 6 -> listOf(
                        Check(
                            name, t, "charge-particles",
                            particles in 200..1200,
                            "蓄力期 PD 粒子 = $particles（黑核 130 + 亮边 168 + 三波丝线 216 + 向心 48 ≈ 560）",
                        ),
                        Check(
                            name, t, "charge-no-damage-yet",
                            damageNumbers(ctx, holder) == 0,
                            "蓄力期间伤害数字 = ${damageNumbers(ctx, holder)}（应为 0：伤害推迟到爆发）",
                        ),
                    )
                    IMPLOSION_CAST + IMPLOSION_CHARGE_TICKS + 4 -> listOf(
                        Check(
                            name, t, "burst-particles",
                            particles >= 120,
                            "爆发期 PD 粒子 = $particles（球体 300 + 外飞 26 + 尚在淡出的核心）",
                        ),
                        Check(
                            name, t, "burst-damage-landed",
                            damageNumbers(ctx, holder) > 0,
                            "爆发后伤害数字 = ${damageNumbers(ctx, holder)}（应 > 0）",
                        ),
                    )
                    IMPLOSION_TICKS - 3 -> listOf(pdParticleCheck(name, t, ctx))
                    else -> emptyList()
                }
            },
        )
    }

    /** 内爆场景：爆炸中心相对脚底的高度（与机位球心一致） */
    private const val IMPLOSION_CENTER_LIFT = 1.0

    /** 内爆场景总长（tick）：放弹 20 + 蓄力 18 + 爆发尾 20 + 余量 */
    private const val IMPLOSION_TICKS = 78

    /** 放弹 tick */
    private const val IMPLOSION_CAST = 20

    /** 与 `large_explosion` 一致：半径 6 / 蓄力 18 tick */
    private const val IMPLOSION_RADIUS = 6.0f
    private const val IMPLOSION_CHARGE_TICKS = 18

    /** 护盾场景总长（tick）：静止 20 + 四次点射 + 两次大爆（各含 18 tick 蓄力）+ 破口 60 + 激光 + 余量 */
    private const val SHIELD_SCENE_TICKS = 232

    /** 大爆从放弹到**结算**的间隔：蓄力 18 tick + 3 tick 余量（伤害在蓄力结束时才落，见 `Explosion`） */
    private const val SHIELD_BLAST_SETTLE = 26

    /** 静止段（不挨打）：屏幕上应当一个粒子都没有 */
    private const val SHIELD_IDLE_END = 20

    /** 四次点射的 tick：火球 `delay 4 / recharge 10` → 间隔取 12，保证每一发都真的能开出来 */
    private const val SHIELD_SHOT_A = 22
    private const val SHIELD_SHOT_B = 34
    private const val SHIELD_SHOT_C = 46
    private const val SHIELD_SHOT_D = 58

    /** 第一次大爆：法力充足 → 抵扣成功（强度 72、抗性 4 → 345 MP） */
    private const val SHIELD_BLAST_OK = 76

    /** 把法力压到不够（345 > 300），并收掉残余火球，让破口窗口里只剩红色轮廓 */
    private const val SHIELD_LOW_MANA_AT = 106

    /** 第二次大爆：法力不够 → 打穿 + 破口（放弹 108 → 蓄力到 126 才结算） */
    private const val SHIELD_BLAST_FAIL = 116

    /** 激光回归（破口已到期之后）：5 格/tick 的高速法术只能靠「命中兜底」拦下 */
    private const val SHIELD_LASER_AT = 208

    /** 测试台法力：满池起步；配合 `charge = 0`（关掉 1000/s 的回复）才能逼出「法力不足」 */
    private const val SHIELD_START_MANA = 3000.0
    private const val SHIELD_LOW_MANA = 300.0

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
    private fun projectileChecks(
        scene: String,
        tick: Int,
        id: String,
        endTick: Int,
        ctx: CaptureDriver.SceneCtx,
    ): List<Check> {
        val alive = MagicManager.projectiles.count { it.id == id && it.alive }
        val total = MagicManager.projectiles.size
        return when (tick) {
            2 -> listOf(Check(scene, tick, "spawned", alive == 1, "alive=$alive total=$total"))
            DEATH_TICK - 2 -> listOf(Check(scene, tick, "alive-before-death", alive == 1, "alive=$alive"))
            endTick -> listOf(
                Check(scene, tick, "cleaned-up", total == 0, "total=$total"),
                pdParticleCheck(scene, tick, ctx),
            )
            else -> emptyList()
        }
    }

    /**
     * **粒子级**的「收干净了」断言：直接问 PD 这个维度里还剩多少颗粒子。
     *
     * 为什么需要它：像素判定只能证明「屏幕上没有亮点了」，证明不了「没有 alpha=0 的孤儿粒子还活着」
     * （第 15/16/17 轮反复踩过残留：球壳粒子是永存的，漏一次 `remove` 就是永久泄漏）。
     * `ParticleManager.getEngine().particleCount()` 是 PD 的公开入口，这里只读一个数字；
     * 它属于 dev-only 的录制工具（`client/capture/`），万一 PD 改名也只影响录制，不影响发布产物。
     */
    private fun pdParticleCheck(scene: String, tick: Int, ctx: CaptureDriver.SceneCtx): Check {
        val count = runCatching { ParticleManager.of(ctx.level).getEngine().particleCount() }.getOrDefault(-1)
        return Check(scene, tick, "pd-particles-zero", count == 0, "remaining=$count")
    }
}
