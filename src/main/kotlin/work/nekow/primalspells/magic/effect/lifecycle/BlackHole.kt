package work.nekow.primalspells.magic.effect.lifecycle

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

/**
 * 黑洞 —— 半径 5 的引力场：
 * - **生物**：迟缓 I，并按 `0.12 / (距离 + 0.5)` 每 tick 拉向中心；
 * - **魔法投射物**：每 tick 沿指向中心的方向加速（吸力同样随距离衰减）—— 把飞进来的法术吸进去；
 * - **粒子**（ParticleDrawing）分三层：
 *   1. **范围球壳**：半径 [EFFECT_RADIUS] 的球面，[SHELL_PARTICLES] 个**永存**粒子，
 *      标识黑洞的**大小/作用范围**；用 `ParticleGroup` 承载 —— **进入淡入、持续自转、退出淡出**
 *      全部在客户端本地求值（**0 包/tick**）；
 *   2. **被吸入的粒子**：每 tick 在球壳上生成 [PARTICLES_PER_TICK] 个（第一颗亮、其余暗紫），
 *      初速度取**随机切向**（绕中心旋转），随后被吸向中心，抵达中心（[CONSUME_RADIUS] 内）即销毁；
 *   3. **进场 / 退场的一次性粒子**（见下）。
 *
 * ## 球壳的进入 / 退出动画（这是本轮新增的「球体的进入、退出动画」）
 *
 * 球壳是**静态形状**，能用的编排只有组级的淡入淡出与旋转，所以「球体长大/缩小」这类径向动画
 * **不能**用 `ParticleGroup.scale` 表达 —— PD 的 `scale`/`pulse` 目前**只缩放粒子的视觉尺寸，
 * 不缩放粒子到轴心的偏移**（实现见 `ClientAnimationProgramManager.sugarFrame`：位置 = 轴心 + 位移 + 相对偏移，
 * 倍率只乘在尺寸上），与它自己的 KDoc（「粒子到轴心的距离与视觉大小同乘」）不符，
 * 已作为 API 需求登记（`docs/issues/pd-api-requests.md`）。本模组**不自造替代实现**，
 * 改用「**一圈独立粒子向心收拢**」来表达同样的观感：
 *
 * - **进入**：球壳 `fadeIn` 淡入 + 自转起步，同时在 [ENTER_BURST_RADIUS_SCALE] 倍半径的外球面上
 *   撒 [ENTER_BURST] 个粒子、初速**指向中心**、速度取 `距离 / 寿命`（[ENTER_BURST_LIFETIME] tick 后
 *   正好落到中心并自行过期）→ 看起来是「外壳物质被吸进来，凝成球壳」；
 * - **退出**：球壳 `fadeOut` 淡出（PD 在动画结束后自行销毁整组），同时在球壳半径上撒 [EXIT_BURST] 个
 *   粒子、同样指向中心、[EXIT_BURST_LIFETIME] tick 内塌缩到中心 → 「整个场塌进奇点」。
 *
 * 两批一次性粒子的速度都是**常量**（生成时给一次初速），位置由 PD 逐 tick 积分 —— **0 包/tick**；
 * 它们自带寿命，不需要本模组再做任何簿记，即使黑洞提前消失也只是多飞不到一秒。
 *
 * ## 吸力：越近越强 + 阻尼（阻尼系数是「能否落到中心」的唯一开关）
 *
 * 加速度随距离增强（`0.12/(距离+0.5)`），并且每 tick 顺带乘一点阻尼（[DAMPING_PER_TICK]）让切向速度衰减。
 * 阻尼太弱时粒子会形成**偏心率很大的椭圆轨道**：半径降到某个值后来回振荡，永远进不了中心。
 * 实测（500 个随机样本数值复算）：`0.995` → 抵达中心 **0/500**（在 r≈2.2~5.0 之间绕到寿命结束）；
 * 现用的 **[DAMPING_PER_TICK] = 0.9** → **100%** 抵达中心，平均 **34 tick**。
 * 参数取值过程见 `docs/issues/resolved.md` 第 11 轮。
 *
 * ## 运动：速度每 tick 由本模组重算，位置积分归 PD
 *
 * 吸力**随距离变化**，PD 的 `applyForce` 那种「一次下发的恒定加速度」表达不了 —— 每 tick 都得重算每颗粒子
 * 该有的速度。但**位置不再由本模组积分**：`ParticleBatch.setVelocityAll(velocities)` 一次包覆盖全组
 * （**1 包/tick，与粒子数无关**），之后由 PD 逐 tick 做 `pos += vel`（服务端权威；暂停时客户端不再外推）。
 *
 * 好处：位置与速度都读 PD 的权威值（`forEach` / `removeIf`），**本模组不持有任何跨 tick 的粒子状态**。
 * 渲染/同步/寿命/可见性依旧由 ParticleDrawing 负责。
 */
class BlackHole : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 0,
    pullPerTick = PULL_PER_TICK,
) {
    /**
     * 范围球壳：`ParticleGroup`（编排式动画），成员永存、铺一次。
     *
     * 为什么从 `ParticleBatch` 换成组：本轮要给球壳加**淡入/淡出/自转**，这三件事都是组级编排，
     * 在组里由客户端本地求值（**0 包/tick**）。代价是不再有 `ParticleBatch.ensureSize` 的「缺了补上」
     * —— 球壳成了**固定成员集**，个别生成被维度上限拒绝时会留空缺（下次进入时重铺）。
     */
    private var shell: ParticleGroup? = null

    /** 球壳建立时的服务端 gameTime：退场动画要把时间线游标推到「现在」，见 [clearVisuals]。 */
    private var shellStartTick = 0L

    /** 被吸入的粒子：成员、速度、位置全部由 PD 维护，本模组只每 tick 重算一次速度。 */
    private var sucked: ParticleBatch? = null

    /** 首 tick 铺球壳（含进场动画），之后每 tick 只补被吸入的粒子。 */
    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        if (shell == null) spawnShell(manager, center)
        spawnSucked(manager, center)
    }

    /**
     * 铺球壳 + 进场动画。
     *
     * 顺序：`createGroup` → `Draw.sphere`（相对中心铺成员）→ `fadeIn` + `spin`。
     * 球壳的轴心就是 [center]（固定世界坐标，黑洞本体不动），所以不需要 `followEntity`。
     */
    private fun spawnShell(manager: ParticleManager, center: Vec3) {
        val group = manager.createGroup(center)
        Draw.sphere(
            manager, center, EFFECT_RADIUS, SHELL_PARTICLES,
            SHELL_COLOR, SHELL_SCALE, group = group,
        )
        group.fadeIn(SHELL_FADE_IN_TICKS, EasingType.EASE_OUT)
        // 缓慢自转：球壳「活着」的最廉价做法（客户端本地累加角度，0 包/tick）
        group.spin(SHELL_SPIN_AXIS, SHELL_SPIN_RADIANS_PER_TICK)
        shell = group
        shellStartTick = manager.level.gameTime

        // 进场：外球面一圈粒子向心收拢，寿命到点正好抵达中心
        spawnInwardBurst(manager, center, ENTER_BURST_RADIUS_SCALE, ENTER_BURST, ENTER_BURST_LIFETIME, INWARD_BURST_COLOR)
    }

    /** 每 tick 在球壳上生成被吸入的粒子（第一颗亮、其余暗紫），初速沿随机切向。 */
    private fun spawnSucked(manager: ParticleManager, center: Vec3) {
        val random = manager.level.random
        val batch = sucked ?: ParticleBatch(manager).also { sucked = it }
        repeat(PARTICLES_PER_TICK) { index ->
            val radial = ParticleShapes.randomUnitVector(random)
            val pos = center.add(radial.scale(EFFECT_RADIUS))
            // 初速度取随机切向（绕中心转）：一次速度包，之后每 tick 由 updateVisuals 重算
            val tangent = ParticleShapes.randomTangent(radial, random).scale(TANGENTIAL_SPEED)
            val spark = index == 0
            batch.add(
                manager.spawnParticle(
                    pos,
                    if (spark) SPARK_COLOR else SUCTION_COLOR,
                    if (spark) SPARK_SCALE else SUCTION_SCALE,
                    LIFETIME_TICKS,
                    tangent,
                )
            )
        }
    }

    /**
     * 每 tick 重算每颗粒子的速度并**一次包**下发；位置由 PD 积分，抵达中心的粒子销毁。
     *
     * 顺序：`forEach` 读 PD 的权威 pos/vel 算出本 tick 速度 → `setVelocityAll` 一包下发 →
     * 再按位置回收（读 PD 权威位置，读不产包）。全程本模组不留跨 tick 状态。
     */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        val batch = sucked ?: return
        if (batch.isEmpty()) return

        // 速度迭代：v ← v·阻尼 + 向心加速度（随距离增强）。位置由 PD 做 pos += v，与这里一一对应
        val velocities = ArrayList<Vec3>(batch.size())
        batch.forEach { _, _, pos, vel ->
            val toCenter = center.subtract(pos)
            velocities += vel.scale(DAMPING_PER_TICK)
                .add(toCenter.normalize().scale(PULL_PER_TICK / (toCenter.length() + 0.5)))
        }
        batch.setVelocityAll(velocities)

        // 抵达中心即销毁（读 PD 的权威位置）
        batch.removeIf { _, pos, _ -> pos.distanceTo(center) <= CONSUME_RADIUS }
    }

    /**
     * 效果结束：球壳**淡出**（退场动画）+ 一整圈粒子从球壳塌缩进中心，被吸入的粒子立即销毁。
     *
     * 球壳走 `fadeOut(removeAfter = true)`：PD 自己排一个延迟任务，动画结束后销毁整组并发停止包 ——
     * 本模组把引用丢掉即可，不需要再回来清理。⚠️ `fadeOut` 的时间线是**相对程序起点**的毫秒数，
     * 所以先用 `delay(已过去 tick)` 把游标推到「现在」，否则指令会被当成「早就该执行完」而瞬间跳到终点。
     */
    override fun clearVisuals(manager: ParticleManager, center: Vec3) {
        shell?.let { group ->
            group.delay(shellAgeTicks(manager))
            group.fadeOut(EXIT_FADE_TICKS, removeAfter = true)
            shell = null
        }
        // 退场：球壳半径上的粒子向心塌缩（速度 = 距离/寿命 → 到点正好抵达中心）
        spawnInwardBurst(manager, center, 1.0, EXIT_BURST, EXIT_BURST_LIFETIME, INWARD_BURST_COLOR)
        sucked?.clear()
        sucked = null
    }

    /** 投射物：每 tick 沿指向中心的方向加速（吸力随距离衰减）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        val strength = (PULL_PER_TICK / (dist + 0.5)).toFloat()
        projectile.status.velocity.add(Vector3f(dirToCenter).mul(strength))
    }

    /** 球壳已经存在了多少 tick（退场动画的时间线游标要对齐到「现在」）。 */
    private fun shellAgeTicks(manager: ParticleManager): Int =
        (manager.level.gameTime - shellStartTick).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * 一次性「向心收拢」粒子：在 [radiusScale] 倍球壳半径的球面上均匀撒点，
     * 初速指向中心、大小 = `距离 / 寿命` —— [lifetimeTicks] tick 后正好落在中心并自行过期。
     *
     * 常量速度、位置由 PD 积分 → **0 包/tick**；这批粒子不属于任何 `ParticleBatch`
     * （不需要按条件回收，也不需要效果结束时清理），自带寿命即可。
     */
    private fun spawnInwardBurst(
        manager: ParticleManager,
        center: Vec3,
        radiusScale: Double,
        count: Int,
        lifetimeTicks: Int,
        color: Color,
    ) {
        val random = manager.level.random
        val radius = EFFECT_RADIUS * radiusScale
        repeat(count) {
            val pos = center.add(ParticleShapes.randomUnitVector(random).scale(radius))
            val velocity = center.subtract(pos).scale(1.0 / lifetimeTicks)
            manager.spawnParticle(pos, color, INWARD_BURST_SCALE, lifetimeTicks, velocity)
        }
    }

    private companion object {
        /** 生效半径：同时是范围球壳与粒子生成半径 */
        const val EFFECT_RADIUS = 5.0

        /** 范围球壳：均匀（Fibonacci）球面取点，粒子越多球体越「实」 */
        const val SHELL_PARTICLES = 144
        const val SHELL_SCALE = 0.5f

        /**
         * 球壳颜色：**上偏紫、下近黑**的半透明渐变（`Draw.sphere` 的 `ColorSource` 参数 t = 从顶到底），
         * 并且每隔 [SPARKLE_EVERY] 颗掺一颗亮紫点当「星点」——纯灰黑在暗处太糊。
         */
        val SHELL_COLOR: ColorSource = ColorSource { t ->
            val base = SHELL_RIM.lerp(SHELL_CORE, t.toFloat())
            if ((t * SHELL_PARTICLES).toInt() % SPARKLE_EVERY == 0) base.lerp(SHELL_SPARK, 0.7f) else base
        }

        /** 球壳上缘（偏紫）/ 下缘（近黑）/ 星点 */
        val SHELL_RIM: Color = Color.ofInt(126, 74, 214, 120)
        val SHELL_CORE: Color = Color.ofInt(26, 18, 46, 165)
        val SHELL_SPARK: Color = Color.ofInt(214, 176, 255, 190)

        /** 每隔几颗掺一个星点 */
        const val SPARKLE_EVERY = 5

        /**
         * 球壳自转轴与角速度：慢速（约 14 秒一圈）—— 球壳是「大小标识」，转太快会抢被吸入粒子的视觉。
         * 想停掉自转就把角速度改成 0.0。
         */
        val SHELL_SPIN_AXIS: Vec3 = Vec3(0.18, 1.0, 0.08).normalize()
        const val SHELL_SPIN_RADIANS_PER_TICK = 0.0075

        /** 球壳淡入时长（tick）—— 进场动画的一半，另一半是 [ENTER_BURST] 的向心收拢 */
        const val SHELL_FADE_IN_TICKS = 14

        /** 每 tick 生成的被吸入粒子数（第一颗是亮紫小点，其余是暗紫大点） */
        const val PARTICLES_PER_TICK = 3

        /** 初始切向速度（blocks/tick）：决定起始绕转快慢 */
        const val TANGENTIAL_SPEED = 0.22

        /** 抵达中心判定半径（方块）：进入这个范围就销毁粒子 */
        const val CONSUME_RADIUS = 0.1

        /** 每次积分的阻尼系数。**这是「能否落到中心」的唯一开关**：0.995 太弱（实测 0/500 到中心，只在 r≈2.2~5.0 绕圈），0.9 → 100% 抵达、平均 34 tick（见类注释） */
        const val DAMPING_PER_TICK = 0.9

        /** 被吸入粒子的尺寸与寿命上限（0.9 阻尼下平均 34 tick 就抵达中心被销毁，这条寿命只是兜底） */
        const val SUCTION_SCALE = 0.55f
        const val SPARK_SCALE = 0.32f
        const val LIFETIME_TICKS = 60

        /** 暗紫（被吸入的主力）与亮紫（每 tick 的第一颗，给球壳一层「星尘」） */
        val SUCTION_COLOR: Color = Color.ofInt(158, 64, 255)
        val SPARK_COLOR: Color = Color.ofInt(226, 196, 255)

        /** 吸力系数（每 tick，对生物与投射物一致，按 1/(距离+0.5) 衰减） */
        const val PULL_PER_TICK = 0.12

        // ---- 进场 / 退场的一次性粒子 ----

        /** 进场收拢粒子的起始半径（球壳半径的倍数）：从球外被吸进来 */
        const val ENTER_BURST_RADIUS_SCALE = 1.45

        /** 进场收拢粒子数 */
        const val ENTER_BURST = 72

        /** 进场收拢时长（tick）：正好等于 [SHELL_FADE_IN_TICKS]，淡入完成时物质刚好到位 */
        const val ENTER_BURST_LIFETIME = SHELL_FADE_IN_TICKS

        /** 退场塌缩粒子数（在球壳半径上撒） */
        const val EXIT_BURST = 96

        /** 退场塌缩时长（tick）与球壳淡出时长（两者一致，塌缩完成的同时球壳刚好淡没） */
        const val EXIT_BURST_LIFETIME = 16
        const val EXIT_FADE_TICKS = EXIT_BURST_LIFETIME

        /** 一次性粒子的尺寸与颜色（亮紫：与球壳的灰黑形成对比，才看得出「物质在动」） */
        const val INWARD_BURST_SCALE = 0.42f
        val INWARD_BURST_COLOR: Color = Color.ofInt(198, 132, 255)
    }
}
