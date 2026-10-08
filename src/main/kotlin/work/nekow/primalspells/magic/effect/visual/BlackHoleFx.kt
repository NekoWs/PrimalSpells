package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ColorSource
import work.nekow.particledrawing.api.Draw
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleGroup
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleStyle
import work.nekow.particledrawing.api.ParticleVisual
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.ParticleShapes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.exp

/**
 * 黑洞的服务端表现接线，由 BlackHole 的三个视觉钩子驱动，每次施法独立持有。
 * 暗核、吸积盘、边界分别编排；固定几何用 PD 组在客户端逐帧旋转，
 * 吸入星尘用 PD 批次读权威位置并批量设速。这里只决定造型与吸力，不实现粒子引擎。
 */
class BlackHoleFx {
    private var core: ParticleGroup? = null
    private var boundary: ParticleGroup? = null
    private var photonRim: ParticleGroup? = null
    private val discs = ArrayList<ParticleGroup>()
    private val outerRings = ArrayList<ParticleGroup>()
    private var sucked: ParticleBatch? = null
    private var emissionIndex = 0
    private var lastCenter: Vec3? = null
    private var ringLag = Vec3.ZERO
    private var ageTicks = 0

    /** 首次铺暗核、倾斜吸积盘与稀疏边界；之后跟随场心、更新外环惯性并补三颗星尘。 */
    fun spawn(manager: ParticleManager, center: Vec3) {
        if (core == null) createStructure(manager, center)
        followCenter(center)
        ageTicks++
        val batch = sucked ?: ParticleBatch(manager).also { sucked = it }
        repeat(3) {
            // 黄金角取点使连续发射均匀铺开，造型可复录，不依赖世界随机数。
            val index = emissionIndex++
            val angle = index * GOLDEN_ANGLE
            val radial = if (index % 3 == 0) {
                DUST_DIRECTIONS[index % DUST_DIRECTIONS.size]
            } else {
                discOffset(1.0, angle, 0.08 * sin(index.toDouble())).normalize()
            }
            val tangent = DISC_NORMAL.cross(radial).normalize().scale(0.22)
            batch.add(
                manager.create().position(center.add(radial.scale(RADIUS)))
                    .color(if (index % 3 == 0) DUST_BRIGHT else DUST_COLOR)
                    .scale(if (index % 3 == 0) 0.55f else 0.8f)
                    .style(ParticleStyle.SQUARE).glowing(true).additive(false)
                    .lifetime(60).spawn()?.also { it.setVelocity(tangent) },
            )
        }
    }

    /** 星尘越靠近暗核越快，位置积分与成员回收仍由 PD 负责，每 tick 一次批量速度包。 */
    fun update(center: Vec3) {
        val batch = sucked ?: return
        batch.removeIf { _, pos, _ -> pos.distanceTo(center) <= CORE_RADIUS }
        if (batch.isEmpty()) return
        val velocities = ArrayList<Vec3>(batch.size())
        batch.forEach { _, _, pos, vel ->
            val delta = center.subtract(pos)
            velocities += vel.scale(0.9).add(delta.normalize().scale(0.12 / (delta.length() + 0.5)))
        }
        batch.setVelocityAll(velocities)
    }

    /** 保持实色，原位旋转着塌缩并按时销毁，直接给现有组追加指令；提前死亡也无需重铺或延迟任务。 */
    fun clear() {
        sucked?.clear()
        sucked = null
        boundary?.scale(0.08f, 10, EasingType.EASE_IN)?.destroyAfter(12)
        boundary = null
        photonRim?.scale(0.06f, 12, EasingType.EASE_IN)?.destroyAfter(16)
        photonRim = null
        for (ring in outerRings) {
            // 表达式接管位置/尺寸，退场收缩也交给同一份 PD 表达式，避免与 scale 指令竞争。
            ring.setVariableLive("endMs", (ageTicks * 50).toString())
            ring.destroyAfter(16)
        }
        outerRings.clear()
        for (disc in discs) {
            disc.scale(0.06f, 12, EasingType.EASE_IN).destroyAfter(16)
        }
        discs.clear()
        core?.scale(0.05f, 16, EasingType.EASE_IN)?.destroyAfter(18)
        core = null
    }

    /** 结构层只生成一次：亮度集中在核边和盘内缘，半径五格的边界留给低亮星点。 */
    private fun createStructure(manager: ParticleManager, center: Vec3) {
        val dark = manager.createGroup(center)
        // 用实色小方粒子密铺球面，保留 MC 的颗粒尺度；球的前后表面自然遮住盘的后半部。
        Draw.sphere(manager, center, CORE_RADIUS, 160, ColorSource.of(CORE_COLOR), 1.4f,
            group = dark, visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))

        core = dark

        repeat(2) { layer ->
            val disc = manager.createGroup(center)
            // 每层三条螺旋，不均匀的亮暗断带比等距同心圆更能读出绕流。
            repeat(3) { arm ->
                Draw.curve(manager, { t ->
                    val radius = CORE_RADIUS + 0.18 + t * (1.50 + layer * 0.20)
                    val angle = arm * PI * 2.0 / 3.0 + t * PI * 1.5 + layer * 0.6
                    center.add(discOffset(radius, angle, (layer - 0.5) * 0.15))
                }, 100,
                    colorFn = ColorSource { t -> if (t < 0.4) DISC_INNER else if (t < 0.75) DUST_COLOR else DISC_OUTER },
                    scale = if (layer == 0) 1.0f else 0.7f, group = disc,
                    visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false))
            }
            disc.spin(DISC_NORMAL, if (layer == 0) 0.065 else 0.035)
            discs += disc
        }

        createPhotonRim(manager, center)
        createOuterRings(manager, center)

        val edge = manager.createGroup(center)
        Draw.sphere(manager, center, RADIUS, 64, ColorSource.of(BOUNDARY_COLOR), 0.55f,
            group = edge, visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))

        boundary = edge
    }

    /**
     * 收敛成一圈较细的核边亮带；去掉穿过暗核的交叉弧，避免看成笼架。
     * 这是粒子造型，不扭曲背景，也不按观察者视角计算透镜像。
     */
    private fun createPhotonRim(manager: ParticleManager, center: Vec3) {
        val group = manager.createGroup(center)
        val visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false)
        Draw.curve(manager, { t -> center.add(discOffset(CORE_RADIUS + 0.13, t * PI * 2.0, 0.0)) }, 128,
            ColorSource.of(DISC_INNER), 0.55f, group = group, visual = visual)

        photonRim = group
    }

    /** 两层外环的形状求值交给 PD 表达式；本模组只更新场心与一个整体的惯性参数。 */
    private fun createOuterRings(manager: ParticleManager, center: Vec3) {
        repeat(2) { layer ->
            val group = manager.createGroup(center)
            Draw.curve(manager, { t -> center.add(discOffset(2.7 + layer * 0.55, t * PI * 2.0, 0.0)) }, 160,
                ColorSource.of(DISC_OUTER), if (layer == 0) 0.75f else 0.55f, group = group,
                visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true).additive(false))
            group.setVariable("cx", center.x).setVariable("cy", center.y).setVariable("cz", center.z)
                .setVariable("lagx", 0.0).setVariable("lagy", 0.0).setVariable("lagz", 0.0)
                .setVariable("rad", 2.7 + layer * 0.55).setVariable("endMs", 1.0e9)
            // i/n 在 PD 客户端求值，少量 spawn 被拒时仍均匀成环，不用自行维护粒子索引表。
            group.expression("""
                ang=2*PI*i/n+t*0.0007;
                px=rad*(${DISC_U.x}*cos(ang)+${DISC_V.x}*sin(ang));
                py=rad*(${DISC_U.y}*cos(ang)+${DISC_V.y}*sin(ang));
                pz=rad*(${DISC_U.z}*cos(ang)+${DISC_V.z}*sin(ang));
                ll=max(0.000001,sqrt(lagx*lagx+lagy*lagy+lagz*lagz));
                along=(px*lagx+py*lagy+pz*lagz)/ll;
                stretch=min(0.65,ll*0.6);
                shrink=max(0.06,1-pow(max(0,t-endMs)/600,2));
                x=cx+(lagx+px+lagx/ll*along*stretch)*shrink;
                y=cy+(lagy+py+lagy/ll*along*stretch)*shrink;
                z=cz+(lagz+pz+lagz/ll*along*stretch)*shrink;
                r=0.55;g=0.32;b=0.9;a=1;sc=shrink;
            """.trimIndent())

            outerRings += group
        }
    }

    /** 核和内盘紧跟最终位置；外环积累有限滞后，停止后指数恢复，瞬移不拖出长线。 */
    private fun followCenter(center: Vec3) {
        val previous = lastCenter ?: center
        val delta = center.subtract(previous)
        val moving = delta.lengthSqr() > 1.0e-10
        if (moving) {
            core?.setPivot(center)
            photonRim?.setPivot(center)
            boundary?.setPivot(center)
            discs.forEach { it.setPivot(center) }
        }
        val oldLag = ringLag
        ringLag = if (delta.lengthSqr() > 16.0) Vec3.ZERO else ringLag.subtract(delta).scale(exp(-0.35))
        if (ringLag.length() > 1.1) ringLag = ringLag.normalize().scale(1.1)
        if (ringLag.lengthSqr() < 1.0e-8) ringLag = Vec3.ZERO
        if (moving || ringLag != oldLag) {
            for (ring in outerRings) {
                ring.setVariableLive("cx", center.x.toString())
                ring.setVariableLive("cy", center.y.toString())
                ring.setVariableLive("cz", center.z.toString())
                ring.setVariableLive("lagx", ringLag.x.toString())
                ring.setVariableLive("lagy", ringLag.y.toString())
                ring.setVariableLive("lagz", ringLag.z.toString())
            }
        }
        lastCenter = center
    }

    /** 固定的倾斜盘面基底，所有盘层及星尘共享，视角绕行时能读出厚度。 */
    private fun discOffset(radius: Double, angle: Double, height: Double): Vec3 =
        DISC_U.scale(radius * cos(angle)).add(DISC_V.scale(radius * sin(angle))).add(DISC_NORMAL.scale(height))

    private companion object {
        const val RADIUS = 5.0
        const val CORE_RADIUS = 0.65
        const val GOLDEN_ANGLE = 2.399963229728653
        val DISC_NORMAL: Vec3 = Vec3(0.28, 1.0, 0.22).normalize()
        val DISC_U: Vec3 = Vec3(1.0, -0.28, 0.0).normalize()
        val DISC_V: Vec3 = DISC_NORMAL.cross(DISC_U).normalize()
        val DUST_DIRECTIONS: List<Vec3> = ParticleShapes.fibonacciSphere(64)
        val CORE_COLOR: Color = Color.ofInt(4, 3, 9, 255)
        val DISC_INNER: Color = Color.ofInt(218, 188, 255, 255)
        val DISC_OUTER: Color = Color.ofInt(90, 43, 175, 255)
        val BOUNDARY_COLOR: Color = Color.ofInt(110, 74, 174, 255)
        val DUST_COLOR: Color = Color.ofInt(153, 80, 245, 255)
        val DUST_BRIGHT: Color = Color.ofInt(214, 186, 255, 255)
    }
}
