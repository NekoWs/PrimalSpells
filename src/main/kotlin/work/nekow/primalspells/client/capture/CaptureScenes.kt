package work.nekow.primalspells.client.capture

import net.minecraft.world.InteractionHand
import net.minecraft.world.item.DyeColor
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.primalspells.entity.DamageNumber
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.lifecycle.AreaFieldEffect
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
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
    val thirdPersonFront: Boolean = false,
    val moveCamera: Boolean = true,
    val fov: Int = 70,
    val showHands: Boolean = false,
    val showHud: Boolean = false,
    val allowScreen: Boolean = false,
    val clientTick: (net.minecraft.client.Minecraft, Int) -> Unit = { _, _ -> },
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
 * | `black_hole` | 暗核 / 双层吸积盘 / 旋转塌缩 | 淡入 6–14t、t=70 死亡、淡出 18t |
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

    fun all(): List<Scene> = listOf(SpellPerformanceScene.scene(), CollisionPrecisionScene.scene(), HomingLightScene.scene(), ParticleLightingScene.scene(), ProjectileFlightScene.scene(), ProgressionScene.scene(), FoundationScene.scene(), HudSawScene.hud(), HudSawScene.saw(), ExplorationScene.scene(), NewSpellScene.scene(), SpellTimingScenes.machinegun(), SpellChainScene.scene(), PdApiScene.scene(), blackHole(), blackHole(true), gravityField(), gravityField(true), energyShield(), explosion(), fireballTrail()) + SpellExpansionScenes.all() + SpellQualityScenes.all() + WandCastingScenes.all()

    // ---------------------------------------------------------------- 黑洞

    /** 黑洞：围绕暗核与倾斜吸积盘公转，录下分层淡入、稳态旋转与原组塌缩退场。 */
    private fun blackHole(moving: Boolean = false): Scene = run {
        val name = if (moving) "black_hole_moving" else "black_hole"
        val lengthTicks = 108
        var wand: Wand? = null
        Scene(
            name = name,
            title = if (moving) "黑洞：暗核与边界星点移动跟随" else "黑洞：暗核 / 星尘吸入与旋流 / 收束退场",
            warmupTicks = 24,
            lengthTicks = lengthTicks,
            fov = 70,
            camera = { ctx, t -> orbit(ctx, FIELD_CENTER_LIFT, radius = 13.0, lift = 3.0, degPerTick = 4.0, tick = if (moving) 0 else t) },
            prepare = { ctx ->
                val floorY = ctx.floorBlockY(CX, CZ)
                // 每场独立清掉摄影棚内部遮挡，避免上次穿墙回归的石墙进入新预览。
                for (x in -12..12) for (z in -12..12) for (y in floorY+1..floorY+12)
                    ctx.level.setBlock(net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2)
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
                // 新弹先经 MagicManager.pendingAdd 入列，再装录制专用定位效果。
                if (moving && t == 3) addFieldMotion("black_hole")
                // t=70 触发正常死亡路径（onDie → 退场动画），而不是删粒子
                if (t == DEATH_TICK) {
                    MagicManager.projectiles.filter { it.id == "black_hole" }.forEach { it.alive = false }
                }
            },
            checksAt = { ctx, t -> projectileChecks(name, t, "black_hole", lengthTicks - 1, ctx) + fieldMotionChecks(name, t, "black_hole", moving) },
        )
    }

    // ---------------------------------------------------------------- 重力场

    /**
     * 重力场：半径 5 的球形范围 + 下坠粒子 + 落地波环。
     *
     * 第 16 轮的验收点是「范围还是要一眼看出是球」：这个场景公转稍快、机位稍远，
     * 让球壳轮廓、内部下坠粒子、地面波环三者在同一帧里都能看到。
     */
    private fun gravityField(moving: Boolean = false): Scene = run {
        val name = if (moving) "gravity_field_moving" else "gravity_field"
        val lengthTicks = 130
        var wand: Wand? = null
        Scene(
            name = name,
            title = if (moving) "重力场：移动球壳与地面井环跟随" else "重力场：球面经线 / 下压刻度波 / 地面双井环",
            warmupTicks = 24,
            lengthTicks = lengthTicks,
            fov = 70,
            camera = { ctx, t -> orbit(ctx, FIELD_CENTER_LIFT, radius = 14.0, lift = 3.5, degPerTick = 3.0, tick = if (moving) 0 else t) },
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
                // 新弹先经 MagicManager.pendingAdd 入列，再装录制专用定位效果。
                if (moving && t == 3) addFieldMotion("gravity_field")
                if (t == DEATH_TICK) {
                    MagicManager.projectiles.filter { it.id == "gravity_field" }.forEach { it.alive = false }
                }
            },
            checksAt = { ctx, t -> projectileChecks(name, t, "gravity_field", lengthTicks - 1, ctx) + fieldMotionChecks(name, t, "gravity_field", moving) },
        )
    }

    /** 录制专用定位效果：走真实 OVERRIDE 阶段，固定镜头便于看出结构有没有留在出生点。 */
    private fun addFieldMotion(id: String) {
        val p = MagicManager.projectiles.single { it.id == id }
        val origin = Vector3d(p.status.pos)
        p.effects += object : BaseEffect() {
            override val phase = EffectPhase.OVERRIDE
            private var age = 0
            override fun onTick() {
                val t = age++
                val offset = when {
                    t < 18 -> 0f
                    t < 33 -> (t - 18) * 0.18f
                    t < 45 -> 2.7f
                    t < 60 -> 2.7f - (t - 45) * 0.36f
                    else -> -2.7f
                }
                status.pos.set((origin.x + offset).toDouble(), origin.y.toDouble(), origin.z.toDouble())
            }
        }.also {
            it.caster = p.caster
            it.wand = p.wand
            it.status = p.status
            it.projectile = p
        }
    }

    private fun fieldMotionChecks(name: String, t: Int, id: String, moving: Boolean): List<Check> {
        if (!moving || t != 25) return emptyList()
        val effects = MagicManager.projectiles.single { it.id == id }.effects.sortedBy { it.phase.tickOrder }
        val field = effects.indexOfFirst { it is AreaFieldEffect }
        val override = effects.indexOfLast { it.phase == EffectPhase.OVERRIDE }
        return listOf(Check(name, t, "field-after-position-override", field > override && override >= 0,
            "field=$field override=$override"))
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
     * 攻击者用的是**普通火球**（强度 **5**）：第 41 轮把一秒预算按小白锚收紧到 **6** 之后，
     * "单发挡得住（5 ≤ 6）、同一窗口第二发超预算（10 > 6）"正好落在这一发上。
     *
     * 时间线（tick）：
     * ```
     *  0-19   静止：屏幕上应当**一个粒子都没有**（护盾平时不可见）
     * 22/48/74/100  四次点射（间隔 26 > 20 tick 窗口）：每一发都**单独**落在预算内 → 挡下、各付 5 MP
     * 134/146        两发落在**同一个 20 tick 窗口**里：累计 10 > 预算 6 → 挡不住（破口 20 tick）
     *                ← 法力充足，所以这一条证明破口来自「窗口预算」而不是「没蓝」（第 39 轮口径）
     * 206    激光：先把法力压到 4（付不起基础 5 MP）→ 打穿（这一发只能靠**命中兜底**拦到）；
     *                强度 0.5 没超出抗性 ⇒ 破口只有基础 20 tick（第 40 轮：破口时长看超出多少）
     * 234    **原版箭矢**（满蓄力、风速 3 ⇒ 强度 6 = 一支小白箭）：应当被**反弹**回去（第 40 轮新增能力）
     * 260    小额爆炸（离持有者 5.75 格，强度 ≈6）：抵扣成功 → **波及到的片全部出玻璃**
     * 270    把持有者挪走 3 格（判定应当跟着人）
     * 276    大爆（摆在**旧球心**处 = 新位置外侧 3 格）：整罩被波及 → 32 片全碎 + 破口吃到 3 秒上限
     * 296    破口期内再走 3 格：**粉色轮廓跟着人走**（白色碎片留在原地）—— 靠帧目视确认
     *        ← 若判定没跟着玩家，爆点会落在旧壳**内部** → 护盾根本不触发，所以这条能证明判定跟人
     * 340-436 **1 个小白**：每 40 tick 一箭（原版骷髅的真实射速：20 拉弓 + 20 间隔）→ 三发全挡下
     * 444-483 **3 个小白**：每 13 tick 一箭 → 第二发就把 20 tick 预算打爆 ⇒ 破口（"不再无敌"那条线）
     * 525    破口到期 → 粒子清零
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
        var manaBeforeArrow = 0.0
        var arrow: Arrow? = null
        // 小白段的状态在**服务端 tick 上记录**，断言稍后再读 —— 场景的 checksAt 跑在客户端计数器上，
        // 与服务器 tick 有 ±10 tick 的漂移，直接"在事件当 tick 断言"会抢跑（第 41 轮踩过）。
        var volleyBreached = false
        var volleyPanels = 0
        var burstBreached = false
        var burstPanels = 0
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
                w.tick(guardDummy, ctx.level) // 首次装备即建立持有证据，避免正逢回收刻的竞态。
                // 攻击者：+X 方向 5.5 格，yaw=90 → 朝 -X 正对护盾持有者
                attacker = ctx.spawnDummy(CX + 5.5, floorY + 1.0, CZ + 0.5, yaw = 90f)
                // **普通火球**（强度 5）：预算 6 之下"单发挡得住（5 ≤ 6）、同一窗口第二发超预算（10 > 6）"才成立 ——
                // 第 41 轮把预算按小白锚收紧到 6 之后，+3 的强化火球（8）会一发就超预算，不适合当"点射"探针
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
                        // 预算是两个常量相乘（第 41 轮起与抗性无关），所以直接算得出来。
                        "budget=${EnergyShield.SKELETON_DPS * EnergyShield.SKELETONS_HELD}"
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
                // 第 39/42 轮「防 DPS」：**按住不放**地连射（每 tick 一发 = 火球基准的 20 发/秒）。
                // 第 42 轮把火球伤害削到 0.5（间隔归零、改用伤害定价）之后，单发强度 0.5 远在抗性 6 之下，
                // "两发就破"不再成立 —— 现在要靠**持续输出**把一秒预算（6）堆爆：第 13 发累计 6.5 > 6 ⇒ 破口。
                // 法力全程充足，所以破口只能来自预算。
                if (t in SHIELD_BURST_AT until SHIELD_BURST_AT + SHIELD_BURST_SHOTS) {
                    sw.spell(atk)
                }
                // 连射的破口在**第 2 发**就发生（火球强度 5、预算 6 ⇒ 5 + 5 > 6），
                // 而破口只持续 20 tick —— 断言又跑在客户端计数器上（与服务器差 ±10 tick），
                // 所以这里在服务端 tick 上把状态记下来，断言稍后再读（第 41 轮踩过的坑）。
                if (t == SHIELD_BURST_AT + SHIELD_HIT_SETTLE - 1) {
                    val shieldCard = shield
                    burstBreached = shieldCard != null && shieldCard.breached
                    burstPanels = shieldCard?.breachPanelCount ?: 0
                    ctx.log("burst @$t: breached=$burstBreached panels=$burstPanels")
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
                    // 箭矢 / 小额爆炸那两段要在"付得起"的前提下验证格挡，所以先把法力补回来
                    w.status.mana = SHIELD_REFILL_MANA
                }
                // ---- 原版投射物（第 40 轮）：满蓄力箭矢从壳外 4 格射来 ⇒ 应当被弹回去 ----
                if (t == SHIELD_ARROW_AT) {
                    manaBeforeArrow = w.status.mana
                    arrow = vanillaArrow(ctx, atk, h)
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
                // ---- 小白段（第 41 轮）：按原版骷髅的真实射速压护盾 ----
                if (t == SHIELD_SKELETON_MANA_AT) {
                    w.status.mana = SHIELD_SKELETON_MANA
                    ctx.log("skeleton phase: mana refilled to ${w.status.mana}")
                }
                if (soloArrowAt(t)) {
                    ctx.log("1 skeleton: arrow at t=$t（每 $SHIELD_SOLO_GAP tick 一箭 = 一个小白）")
                    vanillaArrow(ctx, atk, h)
                }
                if (volleyArrowAt(t)) {
                    ctx.log("3 skeletons: arrow at t=$t（每 $SHIELD_VOLLEY_GAP tick 一箭 = 三个小白）")
                    vanillaArrow(ctx, atk, h)
                }
                // 3 个小白的第二发（457）应当把预算打爆 —— 在第 5 tick 上把状态记下来（服务端时刻是准的）
                if (t == SHIELD_VOLLEY_FIRST + SHIELD_VOLLEY_GAP + 5) {
                    val shieldCard = shield
                    volleyBreached = shieldCard != null && shieldCard.breached
                    volleyPanels = shieldCard?.breachPanelCount ?: 0
                    ctx.log("volley @$t: breached=$volleyBreached panels=$volleyPanels")
                }
                if (t == SHIELD_WALK_AT) {
                    // 破口（264 那次，60 tick）还没到期：挪一步看红色轮廓跟不跟人
                    val pos = h.position()
                    h.setPos(pos.x, pos.y, pos.z + SHIELD_WALK_DISTANCE)
                    h.setOldPosAndRot()
                    ctx.log("holder walked +Z$SHIELD_WALK_DISTANCE during breach → ${h.position()}（红色轮廓应当跟着走）")
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
                        // 四次点射（强化火球 = 强度 8）：每次 15 MP（基础 5 + 超出 2 点 × 5）。
                        // 间隔 26 tick > 窗口 20 ⇒ 每一发都是"窗口里唯一那一发" ⇒ 全都挡得住
                        Check(
                            name, t, "hits-blocked",
                            s != null && !s.breached && w != null && w.status.mana <= SHIELD_START_MANA - 15.0,
                            "breached=${s?.breached} mana=${w?.status?.mana}（四次挡下应 = ${SHIELD_START_MANA - 20}）",
                        ),
                    )
                    SHIELD_BURST_AT + SHIELD_HIT_SETTLE + 16 -> listOf(
                        // 第 39/42 轮的核心断言：**连射**把 20 tick 窗口堆爆 ⇒ 挡不住。
                        // 关键在 mana：它还几乎是满的（只花了 2 发的钱）⇒ 破口**不是**"没蓝"造成的。
                        // 破口状态读的是 `t=145` 在服务端记下的值（避开客户端计数漂移）。
                        Check(
                            name, t, "budget-breach",
                            burstBreached && w != null &&
                                w.status.mana >= SHIELD_START_MANA - 30.0 && damageNumbers(ctx, holder) > 0,
                            "t=145 记录：breached=$burstBreached 面板数=$burstPanels" +
                                " mana=${w?.status?.mana}（应仍 ≥${SHIELD_START_MANA - 30}）" +
                                " 伤害数字=${damageNumbers(ctx, holder)}",
                        ),
                    )
                    SHIELD_ARROW_AT + SHIELD_ARROW_SETTLE -> listOf(
                        // 第 40 轮：**原版箭矢**被挡下并**弹回去** —— 护盾付了基础 5 MP，
                        // 而箭最后停在壳外（若它穿过去命中持有者，会插在持有者身上、x 不会超过壳）
                        Check(
                            name, t, "vanilla-arrow-reflected",
                            arrow != null && w != null &&
                                w.status.mana <= manaBeforeArrow - 4.0 &&
                                arrow.x > (holder?.x ?: 0.0) + 1.0,
                            "mana=${w?.status?.mana}（应 = ${manaBeforeArrow - 5}）" +
                                " 箭 x=${arrow?.x}（持有者 x=${holder?.x}）",
                        ),
                    )
                    SHIELD_LASER_AT + 8 -> listOf(
                        // 法力 4 < 基础 5 MP：预算之内也付不起 ⇒ 打穿。
                        // 「破口出现了」本身就是**命中兜底**跑到了的证据（扫场看不见这一发）
                        Check(
                            name, t, "no-mana-breaking-hit-protected",
                            s != null && s.breached && w != null &&
                                w.status.mana >= SHIELD_LOW_MANA - 0.5 && damageNumbers(ctx, holder) == 0,
                            "breached=${s?.breached} mana=${w?.status?.mana} 伤害数字=${damageNumbers(ctx, holder)}",
                        ),
                        // 破口区 = 受击面板 + 相邻一圈 ⇒ 7 片（五边形受击时 6 片）
                        Check(
                            name, t, "breach-area-one-ring",
                            s != null && s.breachPanelCount in 6..7,
                            "破口面板数=${s?.breachPanelCount}（受击面板 + 相邻一圈）",
                        ),
                    )
                    SHIELD_SMALL_BLAST_AT + SHIELD_ANCHOR_SETTLE -> listOf(
                        // 小额爆炸被抵扣：强度 ≈6 在抗性 6 以内 ⇒ 只付基础 5 MP、**没有**破口 ——
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
                        // 第 39 轮的"波及到的片全部显示"：半径 6 的爆炸摆在壳外 3 格 ⇒ **32 片全部**入选
                        Check(
                            name, t, "blast-reaches-all-panels",
                            s != null && s.breachPanelCount == 32,
                            "破口面板数=${s?.breachPanelCount}（整罩 32 片全被波及；像素判据量不出这件事）",
                        ),
                    )
                    SHIELD_SOLO_CHECK_AT -> listOf(
                        // **1 个小白**（40 tick 一箭）连打 3 发：每发强度 6 = 抗性 6 ⇒ 只付基础 5 MP，
                        // 而 40 tick 的间隔让每发都是"窗口里唯一那一发" ⇒ 全都挡得住（第 41 轮锚：扛 2 个）
                        Check(
                            name, t, "skeleton-dps-held",
                            s != null && !s.breached && w != null &&
                                w.status.mana <= SHIELD_SKELETON_MANA - 14.5,
                            "breached=${s?.breached} mana=${w?.status?.mana}（3 发小白箭应 = ${SHIELD_SKELETON_MANA - 15}）",
                        ),
                    )

                    SHIELD_SCENE_TICKS - 3 -> listOf(
                        // **3 个小白**（13 tick 一箭）：第二发就落在同一个 20 tick 窗口里 ⇒ 6 + 6 > 预算 6
                        // ⇒ 护盾挡不住 —— 这就是"不再友好"那条线（口径：扛 2 个小白，第 3 个破）。
                        // 读的是 t=462 在服务端记下的状态（见 tick 段），避开客户端计数漂移。
                        Check(
                            name, t, "skeleton-volley-breaks",
                            volleyBreached,
                            "t=462 记录：breached=$volleyBreached 破口面板数=$volleyPanels" +
                                "（3 个小白的箭间隔 13 tick ⇒ 两发超预算）",
                        ),
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
        projectile.position = Vector3d((CX + offsetX).toDouble(), (floorY + 2.0f).toDouble(), (CZ + 0.5f).toDouble())
        projectile.velocity = Vector3f(0f, 0f, 0f)
        projectile.spell()
        MagicManager.add(projectile)
    }

    /**
     * **火球拖尾**：跟拍一颗飞行中的火球，看「分层火花尾迹」到底长什么样（第 43 轮新增）。
     *
     * 为什么单独一个场景：其它场景的机位都是**绕固定球心公转**，而拖尾的验收点是
     * 「沿飞行方向拉出的那一条」—— 必须让火球**跑出画面中段**、相机跟着它走，才能看出
     * 「白热芯 + 主色余烬 + 会掉下来的火花」这三层是不是真的成立。
     *
     * 机位 [trailCam] 挂在火球**后上方偏一侧**（后 3.2 / 上 1.6 / 侧 2.4 格），
     * 所以画面里是「火球向右飞出 + 身后的尾迹被甩在左边」的侧后视角。
     *
     * 时间线（tick）：`0` 施法（法师站在 +Z 侧朝 -Z 平射）→ 火球以 ~1 格/tick 飞出 →
     * 命中墙面后自己消失（相机随后停在最后位置，看尾迹在原地飘落/淡出）→ 到 `length` 必须收干净。
     */
    private fun fireballTrail(): Scene = run {
        val name = "fireball_trail"
        var wand: Wand? = null
        val previousAlpha = java.util.IdentityHashMap<work.nekow.particledrawing.core.client.RenderParticle, Float>()
        val alphaFalls = java.util.concurrent.atomic.AtomicInteger()
        val sampling = java.util.concurrent.atomic.AtomicBoolean()
        var minimumAlpha = 1f
        // 高速尾迹仅活1～2t，必须在渲染帧采样；20Hz检查会跳过同一颗粒子的整个淡出段。
        val sampler = java.util.function.Consumer<net.neoforged.neoforge.client.event.RenderFrameEvent.Post> {
            if (sampling.get()) ClientParticleEngine.instance()?.getGlowingParticles()?.forEach { p ->
                val alpha = p.effectiveAlpha()
                if (alpha in 0.01f..0.98f) {
                    minimumAlpha = minOf(minimumAlpha, alpha)
                    previousAlpha[p]?.let { if (alpha < it - 0.02f) alphaFalls.incrementAndGet() }
                    previousAlpha[p] = alpha
                }
            }
        }
        Scene(
            name = name,
            title = "火球拖尾：白热核 + 沿轨迹的符文余痕（跟拍）",
            warmupTicks = 24,
            lengthTicks = TRAIL_TICKS,
            fov = 70,
            // 相机不跟着 player 走（player 就是施法者，站着不动），但**朝向每 tick 由机位函数给**：
            // `moveCamera = true` + 返回「火球当前位置」当目标点即可（见 trailCam）。
            moveCamera = true,
            camera = { ctx, t -> trailCam(ctx, t) },
            prepare = { ctx ->
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(sampler)
                val floorY = ctx.floorBlockY(CX, CZ)
                // 清掉前序场景留下的内部墙，固定死亡tick不应被旧摄影棚提前拦截。
                for (x in CX-TRAIL_STUDIO_HALF..CX+TRAIL_STUDIO_HALF) for (z in CZ-TRAIL_STUDIO_HALF..CZ+TRAIL_STUDIO_HALF)
                    for (y in floorY+1..floorY+TRAIL_STUDIO_HEIGHT)
                        ctx.level.setBlock(net.minecraft.core.BlockPos(x,y,z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2)
                ctx.buildStudio(CX, CZ, floorY, half = TRAIL_STUDIO_HALF, wallHeight = TRAIL_STUDIO_HEIGHT)
                // 默认弹速提升后延长真实摄影棚；保留实际飞行速度与固定死亡刻，不让旧前墙提前截断拖尾。
                val black = net.minecraft.world.level.block.Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState()
                for (z in CZ - 110 until CZ - TRAIL_STUDIO_HALF) {
                    for (x in CX - TRAIL_STUDIO_HALF..CX + TRAIL_STUDIO_HALF)
                        ctx.level.setBlock(net.minecraft.core.BlockPos(x, floorY, z), black, 2)
                    for (y in floorY + 1..floorY + TRAIL_STUDIO_HEIGHT) {
                        ctx.level.setBlock(net.minecraft.core.BlockPos(CX - TRAIL_STUDIO_HALF, y, z), black, 2)
                        ctx.level.setBlock(net.minecraft.core.BlockPos(CX + TRAIL_STUDIO_HALF, y, z), black, 2)
                    }
                }
                for (x in CX - TRAIL_STUDIO_HALF + 1 until CX + TRAIL_STUDIO_HALF)
                    for (y in floorY + 1 until floorY + TRAIL_STUDIO_HEIGHT) {
                        ctx.level.setBlock(net.minecraft.core.BlockPos(x, y, CZ - TRAIL_STUDIO_HALF), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2)
                        ctx.level.setBlock(net.minecraft.core.BlockPos(x, y, CZ - 110), black, 2)
                    }
                ctx.setup(
                    spectator = true,
                    // 站在 +Z 侧、面朝 -Z（yaw 180）：火球沿 -Z 平射，尾迹留在 +Z 一侧的空中
                    x = CX + 0.5,
                    y = floorY + 1.0,
                    z = CZ + TRAIL_CAST_OFFSET,
                    yaw = 180f,
                    pitch = 0f,
                )
                wand = Wand("capture-trail").also { w ->
                    w.innate += MagicManager.create("fireball") ?: error("fireball 未注册")
                    w.status.mana = 1000.0
                    w.load()
                }
                ctx.log("$name prepared: floorY=$floorY castZ=${CZ + TRAIL_CAST_OFFSET}")
            },
            start = { ctx ->
                sampling.set(true)
                val floorY = ctx.floorBlockY(CX, CZ)
                // 施法点 = 眼睛高度；`placeForCast` 不发包（服务端权威），机位仍然由 camera() 给
                ctx.placeForCast(CX + 0.5, floorY + 1.0 + EYE_HEIGHT, CZ + TRAIL_CAST_OFFSET)
                // ⚠️ **必须显式定住施法朝向**（第 43 轮踩）：`placeForCast` 只改位置、不改朝向，
                // 而 `Wand.spell` 读的是玩家当前朝向 —— 预热期机位函数已经在改它了，于是火球会
                // **沿预热的视角斜着飞**（实测 (0.30,-0.21,-0.34)：既偏 +X 又朝下），
                // t≈17 一头扎进地面，尾迹刚建起来就断。这里定成「朝 -Z 平射」，与场景设计一致。
                ctx.player.setYRot(180f)
                ctx.player.setXRot(0f)
                ctx.player.setOldPosAndRot()
                val ok = wand!!.spell(ctx.player)
                ctx.log("$name cast -> $ok, projectiles=${MagicManager.projectiles.size}")
            },
            tick = { ctx, t ->
                // 诊断：每 4 tick 记一行「火球位置 / 速度 / 寿命 / 在场投射物数」——
                // 第 43 轮就是靠它看出「火球沿预热视角斜飞、t≈17 扎进地面」的（像素判据量不出这件事）
                if (t % 4 == 0) {
                    val alive = MagicManager.projectiles.firstOrNull { it.id == "fireball" && it.alive }
                    val state = if (alive == null) "gone" else (
                        "pos=(%.2f,%.2f,%.2f) v=(%.3f,%.3f,%.3f) age=%d".format(
                            alive.status.pos.x, alive.status.pos.y, alive.status.pos.z,
                            alive.status.velocity.x, alive.status.velocity.y, alive.status.velocity.z,
                            alive.status.age,
                        )
                        )
                    ctx.log("trail diag t=$t n=${MagicManager.projectiles.size} bolt=$state")
                }
                // 到点主动收掉火球（而不是等它撞墙）：撞墙时刻取决于棚子尺寸与拖拽，
                // 显式收掉才能让「尾迹逐颗消散」的窗口刚好落在场景后半段（退场判据要 ≥4 帧的渐隐）
                if (t == TRAIL_DEATH_TICK) {
                    MagicManager.projectiles.filter { it.id == "fireball" }.forEach { it.alive = false }
                    ctx.log("trail: bolt released at t=$t (尾迹开始逐颗消散)")
                }
            },
            finish = { _ -> sampling.set(false); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(sampler); previousAlpha.clear() },
            checksAt = { ctx, t ->
                when (t) {
                    // 飞出十几 tick 之后，尾迹应当已经积累出一整条（每 tick 6~11 颗 × 十几 tick）
                    TRAIL_PARTICLE_CHECK -> listOf(
                        Check(
                            name, t, "trail-built-up",
                            pdClientParticleCount() >= TRAIL_MIN_PARTICLES,
                            "客户端 PD 粒子 = ${pdClientParticleCount()}（尾迹累积，应 ≥$TRAIL_MIN_PARTICLES）",
                        ),
                    )
                    32 -> listOf(Check(name, t, "trail-alpha-decreases", alphaFalls.get() > 2 && minimumAlpha < 0.4f,
                        "decreasing=${alphaFalls.get()} minimum=$minimumAlpha"))
                    TRAIL_TICKS - 3 -> listOf(
                        Check(
                            name, t, "trail-cleaned-up",
                            MagicManager.projectiles.none { it.id == "fireball" },
                            "残留火球 = ${MagicManager.projectiles.count { it.id == "fireball" }}",
                        ),
                        pdParticleCheck(name, t, ctx),
                        // 尾迹粒子现在活在**客户端**（发射器）⇒ 泄漏也要在客户端这一侧看
                        Check(
                            name, t, "pd-client-particles-zero",
                            pdClientParticleCount() == 0,
                            "client remaining=${pdClientParticleCount()}",
                        ),
                    )
                    else -> emptyList()
                }
            },
        )
    }

    /**
     * 火球拖尾场景：总长（tick）。
     *
     * ⚠️ 长度必须 ≥ `收球 tick + 死亡补撒的最长寿命 + 余量`（第 43 轮踩）：
     * 死亡补撒的余烬寿命是 8~26 tick、在 `t=[TRAIL_DEATH_TICK]` 撒下，最晚到 `33+26=59` 才到点 ——
     * 场景只给到 60 时，收尾的 `pd-particles-zero` 当场抓到「还剩 1 颗」（差 2 tick 的余量）。
     *
     * 时间线：`t=0` 施法 → 火球朝 -Z 平射（0.5 格/tick，`drag` 很小所以几乎匀速）→
     * `t=[TRAIL_DEATH_TICK]` 主动收掉（走 `onDie` 真实路径，顺带撒一把死亡余烬）→
     * 之后 **39 tick 看尾迹逐颗到点消散**（退场渐变的采样窗口）。
     */
    private const val TRAIL_TICKS = 72

    /** 收掉火球的 tick：显式收（不等撞墙），保证「消散窗口」落在场景后半段 */
    private const val TRAIL_DEATH_TICK = 33

    /** 施法点相对场景中心的 +Z 偏移（棚子够深，机位一直被包在棚内） */
    private const val TRAIL_CAST_OFFSET = 9.0

    /** 影棚半宽（格）：**要够大**，否则回望机位的侧向偏移会把镜头塞进墙里（整屏黑） */
    private const val TRAIL_STUDIO_HALF = 20

    /**
     * 影棚墙高（格）：**必须够高**（第 43 轮踩）。
     *
     * 跟拍机位在火球上方 2.5 格 —— 墙只有 12 高时，相机上升后就从墙体上沿**穿到棚外**，
     * 画面变成「一堵黑墙 + 外面的草地」，尾迹只剩墙角一小撮（实测峰值从 1369 掉到 149）。
     */
    private const val TRAIL_STUDIO_HEIGHT = 22

    /** 尾迹建起来之后采样粒子的 tick（此时火球已飞出一整条尾迹） */
    private const val TRAIL_PARTICLE_CHECK = 20

    /** 这条断言的下限：15 tick × 每 tick 6~11 颗，取一个保守值（尾迹同时存活的粒子远多于此） */
    private const val TRAIL_MIN_PARTICLES = 18

    /** 跟拍机位：跟在火球**后上方偏一侧**，视线锁在火球上（火球没了就停在最后位置）。 */
    private fun trailCam(ctx: CaptureDriver.SceneCtx, t: Int): Pose {
        val floorY = ctx.floorBlockY(CX, CZ)
        val bolt = MagicManager.projectiles.firstOrNull { it.id == "fireball" && it.alive }
        if (bolt != null) {
            lastTrailX = bolt.status.pos.x
            lastTrailY = bolt.status.pos.y
            lastTrailZ = bolt.status.pos.z
            trailSeen = true
            val v = bolt.status.velocity
            val horizontal = sqrt(v.x * v.x + v.z * v.z)
            if (horizontal > 1e-4f) {
                lastTrailDirX = v.x / horizontal
                lastTrailDirZ = v.z / horizontal
            }
        }
        val target = lastTrailTarget()
        // 机位 = 目标点 + 前向 × 前置距离 + 侧向 × 侧距 + 上方抬升（前向取水平方向，避免俯冲时翻镜头）
        val x = target.x + lastTrailDirX * TRAIL_CAM_AHEAD + lastTrailDirZ * TRAIL_CAM_SIDE
        val z = target.z + lastTrailDirZ * TRAIL_CAM_AHEAD - lastTrailDirX * TRAIL_CAM_SIDE
        val y = max(target.y + TRAIL_CAM_LIFT, floorY + 2.0)
        val dx = target.x - x
        val dy = target.y - y
        val dz = target.z - z
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz)))).toFloat()
        return Pose(x, y, z, yaw, pitch)
    }

    /** 火球消失后相机停在最后看到的位置（没有它就退回施法点上方）。 */
    private fun lastTrailTarget(): Vector3f =
        if (trailSeen) Vector3f(lastTrailX.toFloat(), lastTrailY.toFloat(), lastTrailZ.toFloat()) else Vector3f(CX + 0.5f, 4f, CZ + 0.5f)

    // 跟拍机位跨 tick 的缓存（相机函数每个 tick 都要用上一帧的朝向，火球消失后还要停在原地）
    private var trailSeen = false
    private var lastTrailX = 0.0
    private var lastTrailY = 0.0
    private var lastTrailZ = 0.0
    private var lastTrailDirX = 0f
    private var lastTrailDirZ = -1f

    // ---- 跟拍机位常量 ----

    /**
     * 相机沿**飞行方向**的前置距离（格）：**正值 = 机位在火球前方**。
     *
     * ⚠️ 为什么是"前置"而不是跟在后面（第 43 轮连试四组量出来的）：
     * 尾迹永远是**朝后**铺的 —— 相机跟在后面时，尾迹从相机脚下往远方延伸，
     * 尾端很快超出同步半径、屏幕上也压成一条纵深线；而且机位会被"挤"到影棚墙角外
     * （实测出现过整屏黑墙、整屏草地）。改成**前方回望**：火球朝相机飞来，
     * 尾迹从它身后朝远处铺开，构图稳定、尾迹完整，也不会撞墙。
     */
    private const val TRAIL_CAM_AHEAD = 2.0

    /**
     * 相机侧向偏移（格）：决定"从多斜的角度回望"。
     *
     * 与飞行方向的夹角 θ 决定尾迹在屏幕上的投影比（≈sin θ），同时两者一起决定机位到火球的距离
     * （`√(A²+S²)`，越远颗粒子越小、像素判据越吃亏）：
     * `A=5 / S=8` → 9.4 格、θ=58°（太远）；`A=3 / S=5` → 5.8 格、θ=59°；
     * 最终 `A=2 / S=4.2` → **4.65 格**、θ≈64°：第 45 轮把尾迹改短改细之后，
     * 只有靠到这个距离才看得出「一颗颗菱形」而不是一条糊线。
     */
    private const val TRAIL_CAM_SIDE = 4.2

    /** 相机相对火球抬高（格）：略微俯视，尾迹朝画面下方铺开而不是被本体挡住 */
    private const val TRAIL_CAM_LIFT = 2.5

    /** 场上的伤害数字实体数量：护盾挡下时一个都不会有，打穿时才出现（「真的打到了」的直接证据）。 */
    private fun damageNumbers(ctx: CaptureDriver.SceneCtx, holder: TestDummyEntity?): Int {
        val h = holder ?: return 0
        return ctx.level.getEntitiesOfClass(DamageNumber::class.java, h.boundingBox.inflate(8.0)).size
    }

    /** 1 个小白：第 n 发在 `SHIELD_SOLO_FIRST + n × 40`。 */
    private fun soloArrowAt(t: Int): Boolean =
        t >= SHIELD_SOLO_FIRST &&
            t < SHIELD_SOLO_FIRST + SHIELD_SOLO_GAP * SHIELD_SOLO_COUNT &&
            (t - SHIELD_SOLO_FIRST) % SHIELD_SOLO_GAP == 0

    /** 3 个小白：第 n 发在 `SHIELD_VOLLEY_FIRST + n × 13`。 */
    private fun volleyArrowAt(t: Int): Boolean =
        t >= SHIELD_VOLLEY_FIRST &&
            t < SHIELD_VOLLEY_FIRST + SHIELD_VOLLEY_GAP * SHIELD_VOLLEY_COUNT &&
            (t - SHIELD_VOLLEY_FIRST) % SHIELD_VOLLEY_GAP == 0

    /**
     * 从壳外 4 格朝持有者射一支**原版箭矢**（第 40 轮：护盾要能防御/反弹原版投射物）。
     *
     * 用真实的 `Arrow` 实体 + `shoot(power = 3)`（满蓄力速度 3 格/tick）走原版投射物路径：
     * 强度 = 速度 × 2 = **6**（原版箭伤公式），抗性 6 ⇒ 只付基础 5 MP。
     * 归属设成攻击者 —— 这样"自己的弹不弹"这条规则不会被这条断言绕过。
     */
    private fun vanillaArrow(ctx: CaptureDriver.SceneCtx, shooter: TestDummyEntity, holder: TestDummyEntity): Arrow {
        val floorY = ctx.floorBlockY(CX, CZ)
        // ⚠️ 瞄准点必须取**持有者当前**的位置：木桩在第 41 轮的"破口期内走 3 格"之后已经不在 CZ+0.5 了，
        // 写死 z 会让箭从壳边擦过去（实测就是这条让小白段的三发全部打空）。
        val aimY = holder.y + holder.bbHeight * 0.55
        val from = Vector3f(CX + 4.5f, aimY.toFloat(), CZ + 0.5f)
        val aim = Vector3f(holder.x.toFloat(), aimY.toFloat(), holder.z.toFloat())
        val arrow = Arrow(ctx.level, from.x.toDouble(), from.y.toDouble(), from.z.toDouble(), ItemStack(Items.ARROW), null)
        arrow.owner = shooter
        val dir = aim.sub(from).normalize()
        arrow.shoot(dir.x.toDouble(), dir.y.toDouble(), dir.z.toDouble(), SHIELD_ARROW_SPEED, 0.0f)
        ctx.level.addFreshEntity(arrow)
        ctx.log("vanilla arrow fired: from=$from → holder=${holder.position()}")
        return arrow
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
        projectile.position = Vector3d(from)
        projectile.velocity = aim.sub(from).normalize()
        projectile.spell()
        MagicManager.add(projectile)
    }

    private const val DEATH_TICK = 70

    /** 玩家眼睛相对脚底的高度：施法点与机位都按它算 */
    private const val EYE_HEIGHT = 1.62

    private const val SHIELD_CENTER_LIFT = 1.1


    /**
     * **爆炸**：触发即爆 —— 实色碎片向外飞散 + 灰色尘屑 + 收干净。
     *
     * 用**中灰摄影棚**：爆炸的青白色粒子在灰底上对比清楚，且灰底（r≈g≈b）不参与蓝判据。
     *
     * 时间线（tick）：`20` 放一颗 `large_explosion`（半径6）→ age=1死亡触发时同tick结算 → `78` 结束。
     * 场心放一个木桩：死亡触发回调内就该出现伤害数字（第32轮起没有蓄力）。
     */
    private fun explosion(): Scene = run {
        val name = "explosion"
        var holder: TestDummyEntity? = null
        var burstParticles = -1
        var immediateDamage = 0
        var observedAge = -1
        Scene(
            name = name,
            title = "爆炸：白热前锋 / 体积火舌 / 压力碎片 / 翻涌烟团",
            warmupTicks = 24,
            lengthTicks = EXPLOSION_TICKS,
            thirdPerson = false,
            moveCamera = true,
            fov = 60,
            camera = { ctx, t ->
                // 随机火舌最远约18格；机位在范围之外，避免掠过镜头被误判为退场硬切。
                orbit(ctx, centerLift = EXPLOSION_CENTER_LIFT, radius = 20.0, lift = 3.0, degPerTick = 1.0, tick = t)
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
                // 保留摄影相机作为施法者；爆炸也可伤害来源本人，伤害观察目标单独放在爆心旁。
                bolt.caster = ctx.player
                bolt.wand = Wand("capture-explosion")
                bolt.position = Vector3d((CX + 0.5f).toDouble(), (floorY + 2.0f).toDouble(), (CZ + 0.5f).toDouble())
                bolt.velocity = Vector3f(0f, 0f, 0f)
                // 客户端计数可能领先服务端；在真实死亡回调内采样，确保同一次结算已有伤害与粒子。
                // Explosion 已在构造器中先注入，观察器位于它之后，不改变任何结算行为。
                bolt.effects += object : BaseEffect() {
                    override fun onDie() {
                        burstParticles = ParticleManager.of(ctx.level).getEngine().particleCount()
                        immediateDamage = damageNumbers(ctx, holder)
                        observedAge = status.age
                    }
                }
                bolt.spell()
                MagicManager.add(bolt)
                // guard 只是场心的靶子，这里显式用一下，避免"未使用"告警
                check(guard.isAlive)
            },
            finish = {
                holder?.remove(Entity.RemovalReason.DISCARDED)
            },
            checksAt = { ctx, t ->
                when (t) {
                    EXPLOSION_CAST + 12 -> listOf(
                        Check(
                            name, t, "burst-particles",
                            burstParticles in 1200..2200,
                            "死亡结算当tick PD 粒子 = $burstParticles（密集喷射、随机火舌、白热碎裂前锋和飞散余烬）",
                        ),
                        Check(
                            name, t, "damage-immediate",
                            immediateDamage > 0 && observedAge == 1,
                            "死亡结算当tick伤害数字 = $immediateDamage，法术age=$observedAge（要求age=1即有伤害）",
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
     * 护盾场景总长（tick）：静止 20 + 四次点射 + 窗口内连挨两发 + 法力不足 + 原版箭矢 + 小爆 + 挪位 + 大爆 + 余量。
     *
     * ⚠️ 末尾留 **20 tick** 余量：大爆的破口轮廓在 `T + 54 停留 + 6 淡出 + 5 余量 = T+65` 才被 PD 销毁，
     * 而收尾断言 `pd-particles-zero` 在倒数第 3 帧 —— 余量给少了这条断言就会和销毁时点抢跑。
     */
    private const val SHIELD_SCENE_TICKS = 528

    /** 补满法力（原版箭矢 / 小额爆炸那两段要在"付得起"的前提下验证格挡） */
    private const val SHIELD_REFILL_AT = 230
    private const val SHIELD_REFILL_MANA = 3000.0

    /**
     * 破口还没到期时把持有者往 +Z 挪 3 格：**粉色破口轮廓应当跟着人走**、白色碎片留在原地（第 40 轮口径）。
     *
     * ⚠️ 跟随是客户端 `followEntity` 的行为，服务端读不到渲染位置 ⇒ 这一条**没有自动断言**，
     * 靠产物的帧目视确认（取 280 前后各一帧对比）。
     */
    private const val SHIELD_WALK_AT = 296
    private const val SHIELD_WALK_DISTANCE = 3.0f

    /** 原版箭矢：从壳外 4 格正对持有者射一支（满蓄力 = 速度 3 格/tick ⇒ 强度 = 速度 × 2 = 6） */
    private const val SHIELD_ARROW_AT = 234
    private const val SHIELD_ARROW_SPEED = 3.0f
    private const val SHIELD_ARROW_SETTLE = 8

    /** 判定跟人的验证：把持有者往 +X 挪这么远，再把大爆摆在旧球心处 */
    private const val SHIELD_MOVE_DISTANCE = 3.0f
    private const val SHIELD_MOVE_AT = 270

    /**
     * 小额爆炸（相对竞技场中心 CX 的偏移，木桩在 CX+0.5）：离持有者约 5.75 格 ⇒
     * 衰减后伤害 = 24 × (1 − 5.75/6) ≈ 1、强度 = 衰减后伤害 × 半径 6 ≈ **6** ⇒
     * 落在抗性 6 以内 ⇒ 抵扣成功（只付基础 5 MP）。
     */
    private const val SHIELD_SMALL_BLAST_OFFSET = -5.25f
    private const val SHIELD_SMALL_BLAST_AT = 260

    /** 大爆：摆在**旧球心**处 —— 对挪走 3 格后的持有者来说它在壳外 3 格（强度 ≈72，破口吃满上限 3 秒） */
    private const val SHIELD_BIG_BLAST_OFFSET = 0.5f
    private const val SHIELD_BIG_BLAST_AT = 276

    /** 放弹到结算的间隔：触发即爆，留几 tick 余量 */
    private const val SHIELD_ANCHOR_SETTLE = 6

    /** 火球从发射到被壳面拦下的间隔（实测 ≈11 tick）+ 余量：判定要看的是"拦下之后"的状态 */
    private const val SHIELD_HIT_SETTLE = 16

    /** 静止段（不挨打）：屏幕上应当一个粒子都没有 */
    private const val SHIELD_IDLE_END = 20

    /** 四次点射的 tick：火球 `delay 4 / recharge 10`（强化再 +2）→ 间隔取 26，每发都单独落在一个窗口里 */
    private const val SHIELD_SHOT_A = 22
    private const val SHIELD_SHOT_B = 48
    private const val SHIELD_SHOT_C = 74
    private const val SHIELD_SHOT_D = 100

    /**
     * 连射段（第 42 轮口径）：每 tick 一发，共 [SHIELD_BURST_SHOTS] 发。
     *
     * 火球单发强度 5（Noita 式：法术给伤害、法杖给节奏）⇒ **第 2 发就 5 + 5 > 预算 6 ⇒ 破口**；
     * 连射只是把"持续输出压破护盾"这件事演出来（第 2 发之后每一发都从破口里穿过去）。
     */
    private const val SHIELD_BURST_AT = 130
    private const val SHIELD_BURST_SHOTS = 16

    /** 把法力压到付不起基础 5 MP（并收掉残余火球，让破口窗口里只剩红色轮廓） */
    private const val SHIELD_LOW_MANA_AT = 200

    /** 激光：5 格/tick 的高速法术只能靠「命中兜底」拦下（第 26 轮回归） */
    private const val SHIELD_LASER_AT = 206

    /** 测试台法力：满池起步；配合 `charge = 0`（关掉 1000/s 的回复）才能逼出「法力不足」 */
    private const val SHIELD_START_MANA = 3000.0

    /** 压到的"不够付基础 5 MP"的法力 */
    private const val SHIELD_LOW_MANA = 4.0

    // ---- 小白段（第 41 轮）：按原版骷髅的真实 DPS 压护盾 ----

    /** 小白段开始前补满法力（这一段的账要是干净的：3 发只花 15 MP） */
    private const val SHIELD_SKELETON_MANA_AT = 340
    private const val SHIELD_SKELETON_MANA = 3000.0

    /** **1 个小白**：40 tick 一箭（原版：20 tick 拉弓 + 20 tick 间隔 = 2 秒），共 3 发 → 应当全挡下 */
    private const val SHIELD_SOLO_FIRST = 344
    private const val SHIELD_SOLO_GAP = 40
    private const val SHIELD_SOLO_COUNT = 3

    /** 单人小白那一条断言的 tick：最后一发（424）结算完再等 18 tick，避开客户端计数漂移 */
    private const val SHIELD_SOLO_CHECK_AT = SHIELD_SOLO_FIRST + SHIELD_SOLO_GAP * (SHIELD_SOLO_COUNT - 1) + 18

    /** **3 个小白**：13 tick 一箭（40 / 3）→ 两发就落进同一个 20 tick 窗口 ⇒ 12 > 预算 6 ⇒ 破口 */
    private const val SHIELD_VOLLEY_FIRST = 444
    private const val SHIELD_VOLLEY_GAP = 13
    private const val SHIELD_VOLLEY_COUNT = 4

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
        val count = pdParticleCount(ctx)
        return Check(scene, tick, "pd-particles-zero", count == 0, "remaining=$count")
    }

    /** 服务端当前存活的 PD 粒子数（读不到给 -1，判定会如实失败而不是假装通过）。 */
    private fun pdParticleCount(ctx: CaptureDriver.SceneCtx): Int =
        runCatching { ParticleManager.of(ctx.level).getEngine().particleCount() }.getOrDefault(-1)

    /**
     * **客户端**当前存活的 PD 粒子数。
     *
     * ⚠️ 第 48 轮起尾迹改用**发射器**（PD 1.0.14）：粒子在客户端按渲染帧生成、服务端只有声明 ⇒
     * 旧的「服务端 PD 粒子数 ≥ 18」必然接近 0 而误报。口径跟着模型走：这一层的证据改从客户端取
     * （dev-only 录制工具只读一个数字；PD 改名也只影响录制，不影响发布产物）。
     */
    private fun pdClientParticleCount(): Int =
        runCatching { ClientParticleEngine.instance()?.activeCount() ?: -1 }.getOrDefault(-1)
}
