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

    fun all(): List<Scene> = listOf(blackHole(), gravityField(), energyShield(), explosion())

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
     * 玻璃护盾：**平时完全不可见** → 受击处展开玻璃板 → 挡不住时那一片碎裂 + 破口轮廓
     * → 爆炸把**波及到的片全部**打碎（第 39 轮口径）。
     *
     * 攻击者是**第二个测试木桩**（`caster` 必须不是护盾持有者，否则投射物会被
     * `p.caster === player` 排除），拿一根只装火球的法杖，站在 +X 方向 5.5 格外平射 ——
     * 走真实施法路径，护盾走真实弹开路径。被动驱动走真实路径：`Wand.tick(caster, level)`。
     *
     * 时间线（tick）：
     * ```
     *  0-19   静止：屏幕上应当**一个粒子都没有**（护盾平时不可见）
     * 22/48/74/100  四次点射（间隔 26 > 20 tick 窗口）：每一发都**单独**落在预算内 → 挡下、各付 10 MP
     * 134/146        两发火球落在**同一个 20 tick 窗口**里：累计 10 > 预算 8 → 挡不住
     *                ← 法力充足，所以这一条证明破口来自「窗口预算」而不是「没蓝」（第 39 轮新口径）
     * 226    激光：先把法力压到 4（付不起基础 5 MP）→ 打穿（这一发只能靠**命中兜底**拦到）
     * 292    小额爆炸（离持有者 5.75 格，强度 ≤ 6）：抵扣成功 → **波及到的片全部出玻璃**
     * 302    把持有者挪走 3 格（判定应当跟着人）
     * 308    大爆（摆在**旧球心**处 = 新位置外侧 3 格）：整罩被波及 → 32 片全碎 + 破口
     *        ← 若判定没跟着玩家，爆点会落在旧壳**内部** → 护盾根本不触发，所以这条能证明判定跟人
     * 374    破口到期（368）→ 粒子清零
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
            title = "玻璃护盾：平时不可见 → 受击玻璃板 → 超预算挡不住 + 破口 → 爆炸把波及到的片全打碎",
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
                        "resistance=${card.baseResistance + card.resistancePerCard} " +
                        // ⚠️ 不能用 `card.strengthBudget()`：它要读卡上的 `wand`，而那一刻 `wand` 还没初始化
                        // （`Magic.wand` 是 lateinit，由 tick/管理器路径写入）——实测会抛
                        // `UninitializedPropertyAccessException` 并把 prepare 记成 FAILED。
                        "budget=${(card.baseResistance + card.resistancePerCard) * EnergyShield.RESISTANCE_BUDGET_RATIO}"
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
                // 第 39 轮「防 DPS」：两发火球挨得足够近（间隔 12 tick），一起落在同一个 20 tick 窗口里
                // ⇒ 累计强度 10 > 预算 8 ⇒ 第二发挡不住。法力全程充足，所以破口只能来自预算。
                if (t == SHIELD_PAIR_A || t == SHIELD_PAIR_B) {
                    sw.spell(atk)
                }
                // 激光回归（第 26 轮）：高速法术（5 格/tick、maxAge 1）在同一 tick 里就穿壳贴脸，
                // 只有 `Hurt` 的命中兜底拦得到它 —— 这一发法力不够（4 < 基础 5）⇒ 应当**打穿**，
                // 于是「破口出现了」本身就是兜底跑到了的证据。
                if (t == SHIELD_LASER_AT) {
                    laser(ctx, atk, sw)
                }
                if (t == SHIELD_LOW_MANA_AT) {
                    // 把法力压到付不起基础 5 MP，并收掉残余火球
                    // —— 后者的橙色拖尾会污染破口窗口的「暖色像素」判据
                    w.status.mana = SHIELD_LOW_MANA
                    MagicManager.projectiles.forEach { it.alive = false }
                }
                if (t == SHIELD_REFILL_AT) {
                    // 小额爆炸那一段要在"付得起"的前提下验证抵扣，所以先把法力补回来
                    w.status.mana = SHIELD_REFILL_MANA
                }
                if (t == SHIELD_SMALL_BLAST_AT) {
                    // 小额爆炸：摆在 5.75 格外 ⇒ 强度 = 144 × (1 − d/6) ≤ 6，落在「抗性 4 < 强度 ≤ 预算 8」里
                    // ⇒ 抵扣成功，且**波及到的片全部出玻璃**（第 39 轮的表现口径）
                    manaBeforeBlast = w.status.mana
                    blast(ctx, atk, sw, offsetX = SHIELD_SMALL_BLAST_OFFSET)
                }
                // ---- 判定跟人（第 33/36 轮）：把**持有者挪走**，看护盾的几何跟不跟 ----
                if (t == SHIELD_MOVE_AT) {
                    val pos = h.position()
                    h.setPos(pos.x + SHIELD_MOVE_DISTANCE, pos.y, pos.z)
                    h.setOldPosAndRot()
                    ctx.log("holder moved +$SHIELD_MOVE_DISTANCE → ${h.position()}")
                }
                if (t == SHIELD_BIG_BLAST_AT) {
                    // 爆点摆在**旧球心**处：对挪走后的持有者来说它在 3 格外（壳外 ⇒ 触发），
                    // 但若判定还留在原地，它就在旧壳**内部** ⇒ 护盾根本不触发。
                    // 所以「触发了」= 判定确实跟着玩家走（这条比上一轮的"锚定"探针决定性得多）。
                    manaBeforeBlast = w.status.mana
                    blast(ctx, atk, sw, offsetX = SHIELD_BIG_BLAST_OFFSET)
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
                    SHIELD_SHOT_D + SHIELD_HIT_SETTLE -> listOf(
                        // 四次点射都落在抗性之外一点点：每次 10 MP（基础 5 + 超出 1 点 × 5）。
                        // 间隔 26 tick > 窗口 20 ⇒ 每一发都是"窗口里唯一那一发" ⇒ 全都挡得住
                        Check(
                            name, t, "hits-blocked",
                            s != null && !s.breached && w != null && w.status.mana <= SHIELD_START_MANA - 35.0,
                            "breached=${s?.breached} mana=${w?.status?.mana}（四次挡下应 = ${SHIELD_START_MANA - 40}）",
                        ),
                    )
                    SHIELD_PAIR_B + SHIELD_HIT_SETTLE -> listOf(
                        // 第 39 轮的核心断言：两发落在同一个窗口里 ⇒ 累计强度 10 > 预算 8 ⇒ 挡不住。
                        // 关键在 mana：它还几乎是满的（只花了 5 发的钱）⇒ 破口**不是**"没蓝"造成的
                        Check(
                            name, t, "budget-breach",
                            s != null && s.breached && w != null &&
                                w.status.mana >= SHIELD_START_MANA - 60.0 && damageNumbers(ctx, holder) > 0,
                            "breached=${s?.breached} mana=${w?.status?.mana}（应仍 ≥${SHIELD_START_MANA - 60}）" +
                                " 伤害数字=${damageNumbers(ctx, holder)}",
                        ),
                    )
                    SHIELD_LASER_AT + 8 -> listOf(
                        // 法力 4 < 基础 5 MP：预算之内也付不起 ⇒ 打穿。
                        // 「破口出现了」本身就是**命中兜底**跑到了的证据（扫场看不见这一发）
                        Check(
                            name, t, "no-mana-breach",
                            s != null && s.breached && w != null &&
                                w.status.mana >= SHIELD_LOW_MANA - 0.5 && damageNumbers(ctx, holder) > 0,
                            "breached=${s?.breached} mana=${w?.status?.mana} 伤害数字=${damageNumbers(ctx, holder)}",
                        ),
                    )
                    SHIELD_SMALL_BLAST_AT + SHIELD_ANCHOR_SETTLE -> listOf(
                        // 小额爆炸被抵扣：法力掉 5~15（强度 ≤6）且**没有**破口 ——
                        // 也就是说这一发只是把「波及到的片」全亮了一下（第 39 轮的表现口径）
                        Check(
                            name, t, "small-blast-absorbed",
                            s != null && !s.breached && w != null &&
                                w.status.mana <= manaBeforeBlast - 4.0 && w.status.mana >= manaBeforeBlast - 30.0,
                            "breached=${s?.breached} mana=${w?.status?.mana} before=$manaBeforeBlast",
                        ),
                    )
                    SHIELD_BIG_BLAST_AT + SHIELD_ANCHOR_SETTLE -> listOf(
                        // 判定跟人：木桩挪走 3 格后，爆点落在**旧球心** —— 判定跟着玩家 ⇒ 它在壳外 3 格
                        // ⇒ 触发整罩破口。若判定还留在原地，爆点会在旧壳**内部**，护盾根本不触发
                        Check(
                            name, t, "blast-triggers-after-move",
                            s != null && s.breached,
                            "breached=${s?.breached}（爆点在旧球心：触发 = 判定确实跟着玩家）",
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
    private fun blast(
        ctx: CaptureDriver.SceneCtx,
        caster: TestDummyEntity,
        wand: Wand,
        offsetX: Float = 3.5f,
    ) {
        val floorY = ctx.floorBlockY(CX, CZ)
        val projectile = MagicManager.create("large_explosion") as Projectile
        projectile.caster = caster
        projectile.wand = wand
        projectile.position = Vector3f(CX + offsetX, floorY + 2.0f, CZ + 0.5f)
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
     * **爆炸**：触发即爆 —— 球壳粒子云撑到爆炸半径 + 内部闪电丝 + 向外飞散 + 收干净。
     *
     * 用**中灰摄影棚**：爆炸的青白色粒子在灰底上对比清楚，且灰底（r≈g≈b）不参与蓝判据。
     *
     * 时间线（tick）：`20` 放一颗 `large_explosion`（半径 6）→ **同 tick 立刻结算** → `78` 结束。
     * 场心放一个木桩：**放弹后立刻**就该出现伤害数字（第 32 轮起没有蓄力）。
     */
    private fun explosion(): Scene = run {
        val name = "explosion"
        var holder: TestDummyEntity? = null
        Scene(
            name = name,
            title = "爆炸：触发即爆（粒子云撑到半径 + 外飞 + 烟渐隐）",
            warmupTicks = 24,
            lengthTicks = EXPLOSION_TICKS,
            thirdPerson = false,
            moveCamera = true,
            fov = 60,
            camera = { ctx, t ->
                orbit(ctx, centerLift = EXPLOSION_CENTER_LIFT, radius = 12.0, lift = 3.0, degPerTick = 1.0, tick = t)
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
                ctx.log("$name prepared: radius=$EXPLOSION_RADIUS")
            },
            tick = { ctx, t ->
                if (t != EXPLOSION_CAST) return@Scene
                val guard = holder ?: return@Scene
                val floorY = ctx.floorBlockY(CX, CZ)
                val bolt = MagicManager.create("large_explosion") as Projectile
                // ⚠️ 施法者必须是**别人**：applyDamageAndKnockback 会排除 source 实体
                bolt.caster = ctx.player
                bolt.wand = Wand("capture-explosion")
                bolt.position = Vector3f(CX + 0.5f, floorY + 2.0f, CZ + 0.5f)
                bolt.velocity = Vector3f(0f, 0f, 0f)
                bolt.spell()
                MagicManager.add(bolt)
                // guard 只是场心的靶子，这里显式用一下，避免"未使用"告警
                check(guard.isAlive)
            },
            finish = {
                holder?.remove(Entity.RemovalReason.DISCARDED)
            },
            checksAt = { ctx, t ->
                val particles = runCatching {
                    ParticleManager.of(ctx.level).getEngine().particleCount()
                }.getOrDefault(-1)
                when (t) {
                    EXPLOSION_CAST + 3 -> listOf(
                        Check(
                            name, t, "burst-particles",
                            particles >= 200,
                            "爆后 PD 粒子 = $particles（球壳粒子云 + 外飞）",
                        ),
                        Check(
                            name, t, "damage-immediate",
                            damageNumbers(ctx, holder) > 0,
                            "放弹后伤害数字 = ${damageNumbers(ctx, holder)}（第 32 轮起无蓄力，应当立刻有）",
                        ),
                    )
                    EXPLOSION_TICKS - 3 -> listOf(pdParticleCheck(name, t, ctx))
                    else -> emptyList()
                }
            },
        )
    }

    /** 爆炸场景：爆炸中心相对脚底的高度（与机位球心一致） */
    private const val EXPLOSION_CENTER_LIFT = 1.0

    /** 爆炸场景总长（tick）：放弹 20 + 爆发尾 40 + 余量 */
    private const val EXPLOSION_TICKS = 78

    /** 放弹 tick */
    private const val EXPLOSION_CAST = 20

    /** 与 `large_explosion` 一致：半径 6 */
    private const val EXPLOSION_RADIUS = 6.0f

    /**
     * 护盾场景总长（tick）：静止 20 + 四次点射 + 窗口内连挨两发 + 法力不足 + 小爆 + 挪位 + 大爆 + 破口 + 余量。
     *
     * ⚠️ 末尾留 **22 tick** 余量（大爆的破口轮廓在 `T+65` 才被 PD 销毁：54 停留 + 6 淡出 + 5 余量，
     * 而收尾断言 `pd-particles-zero` 在倒数第 3 帧）—— 余量给少了这条断言就会和销毁时点抢跑。
     */
    private const val SHIELD_SCENE_TICKS = 390

    /** 补满法力（小额爆炸那一段要在"付得起"的前提下验证抵扣） */
    private const val SHIELD_REFILL_AT = 288
    private const val SHIELD_REFILL_MANA = 3000.0

    /** 判定跟人的验证：把持有者往 +X 挪这么远，再把大爆摆在旧球心处 */
    private const val SHIELD_MOVE_DISTANCE = 3.0f
    private const val SHIELD_MOVE_AT = 302

    /**
     * 小额爆炸（相对竞技场中心 CX 的偏移，木桩在 CX+0.5）：离持有者约 5.75 格 ⇒
     * 衰减后伤害 = 24 × (1 − 5.75/6) ≈ 1、强度 = 强度 × 半径 6 ≤ **6** ⇒
     * 落在「抗性 4 < 强度 ≤ 预算 8」这条带里 ⇒ 抵扣成功（付 5 + 2 点 × 5 ≈ 15 MP）。
     */
    private const val SHIELD_SMALL_BLAST_OFFSET = -5.25f
    private const val SHIELD_SMALL_BLAST_AT = 292

    /** 大爆：摆在**旧球心**处 —— 对挪走 3 格后的持有者来说它在壳外 3 格（强度 ≈60，必破） */
    private const val SHIELD_BIG_BLAST_OFFSET = 0.5f
    private const val SHIELD_BIG_BLAST_AT = 308

    /** 放弹到结算的间隔：触发即爆，留几 tick 余量 */
    private const val SHIELD_ANCHOR_SETTLE = 6

    /** 火球从发射到被壳面拦下的间隔（实测 ≈11 tick）+ 余量：判定要看的是"拦下之后"的状态 */
    private const val SHIELD_HIT_SETTLE = 16

    /** 静止段（不挨打）：屏幕上应当一个粒子都没有 */
    private const val SHIELD_IDLE_END = 20

    /** 四次点射的 tick：火球 `delay 4 / recharge 10` → 间隔取 26，保证每一发都单独落在一个窗口里 */
    private const val SHIELD_SHOT_A = 22
    private const val SHIELD_SHOT_B = 48
    private const val SHIELD_SHOT_C = 74
    private const val SHIELD_SHOT_D = 100

    /** 窗口内连挨两发（间隔 12 = recharge）：累计强度 10 > 预算 8 ⇒ 第二发挡不住 */
    private const val SHIELD_PAIR_A = 134
    private const val SHIELD_PAIR_B = 146

    /** 把法力压到付不起基础 5 MP（并收掉残余火球，让破口窗口里只剩红色轮廓） */
    private const val SHIELD_LOW_MANA_AT = 220

    /** 激光：5 格/tick 的高速法术只能靠「命中兜底」拦下（第 26 轮回归） */
    private const val SHIELD_LASER_AT = 226

    /** 测试台法力：满池起步；配合 `charge = 0`（关掉 1000/s 的回复）才能逼出「法力不足」 */
    private const val SHIELD_START_MANA = 3000.0

    /** 压到的"不够付基础 5 MP"的法力 */
    private const val SHIELD_LOW_MANA = 4.0

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
