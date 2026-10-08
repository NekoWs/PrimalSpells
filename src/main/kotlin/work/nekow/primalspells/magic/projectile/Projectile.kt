package work.nekow.primalspells.magic.projectile

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.geometry.Move
import work.nekow.primalspells.magic.effect.geometry.Position
import work.nekow.primalspells.magic.effect.visual.Trajectory
import work.nekow.primalspells.magic.effect.geometry.Velocity
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.util.HitType
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.util.LoreEntry
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.MagicStatus

abstract class Projectile: Magic() {
    var status: MagicStatus = MagicStatus()
    /** 独立的施法配方；只保存id计数，不保存共享effect实例。 */
    val modifiers = linkedMapOf<String, Int>()
    /** 原始抽牌顺序，用于后代重放不可交换的修饰。 */
    val modifierOrder = arrayListOf<String>()
    /** 扩散/聚爆同时缩放弹体碰撞、光束宽度和场半径。 */
    var areaScale: Double = 1.0
    /** 被其它法术注入的能量，供水晶/黑洞释放。 */
    var storedEnergy: Double = 0.0

    override var cast: Int = 0

    /** 投射物本体粒子的颜色（由各投射物子类覆盖）。 */
    open val bodyColor: Color = Color.WHITE

    var maxAge: Int = 0
    var alive: Boolean = true

    /** 粘附类法术关闭 Move 积分；轨道 OVERRIDE 仍可组合。 */
    var motionEnabled: Boolean = true

    /** 施放时原始方向，静止束源和触发荷载也保留来向。 */
    var launchDirection: net.minecraft.world.phys.Vec3 = net.minecraft.world.phys.Vec3.ZERO
        private set

    var maxBounces: Int = 0
    var maxHitTargets: Int = 1
    var bounceScaler: Vector3f = Vector3f(0.9f, 0.9f, 0.9f)

    lateinit var position: Vector3f
    lateinit var velocity: Vector3f
    var dragCoeff: Float = 0.02f
    var speed: Float = 1f
    var hitRadius: Double = 0.0
    var throughBlocks: Boolean = false
    var selfDamage: Boolean = false

    override fun spell() {
        // 内建效果按阶段执行（见 EffectPhase）：
        //   SETUP  —— Position / Velocity 写入出生点与初速
        //   MOTION —— Move 每 tick 做位置积分与碰撞
        //   VISUAL —— Trajectory 读本 tick 的最终位置发粒子
        // 修饰器注入的效果默认落在 MODIFIER 阶段：onActive 在 SETUP 之后（能改初速/伤害），
        // onTick 在 MOTION 之前（改的是速度这类输入量）。
        launchDirection = if (velocity.lengthSquared() > 1e-8f)
            net.minecraft.world.phys.Vec3(velocity.x.toDouble(), velocity.y.toDouble(), velocity.z.toDouble()).normalize()
            else caster.lookAngle
        work.nekow.primalspells.magic.core.SpellAssembly.prepare(this)
        effects += work.nekow.primalspells.magic.effect.lifecycle.SpellResonance()
        effects += Position(position)
        effects += Velocity(Vector3f(velocity).mul(speed))
        effects += Move(
            dragCoeff,
            hitRadius = hitRadius,
            throughBlocks = throughBlocks,
            selfDamage = selfDamage,
        )
        // 本体标记交给 Trajectory 的头部白热菱形（`track`，逐渲染帧丝滑）：不再单独挂一个柔光点，
        // 否则同一个"本体位置"会有两套时钟（track vs 发射器锚点），相位差会看成闪烁。

        effects.forEach {
            it.projectile = this
            it.status = status
        }

        super.spell()

        status.bounces = maxBounces
        status.hitTargets = 0
    }

    /** 静止/重叠碰撞也有有限单位来向，避免将NaN传给触发荷载和分裂弹。 */
    private fun currentDirection(): Vector3f = if (status.velocity.lengthSquared() > 1e-8f)
        Vector3f(status.velocity).normalize() else Vector3f(launchDirection.x.toFloat(), launchDirection.y.toFloat(), launchDirection.z.toFloat())

    fun hitEntity(entity: Entity) {
        val offset = Vector3f(status.pos).sub(entity.position().toVector3f())
        val direction = currentDirection()
        val impactNormal = if (offset.lengthSquared() > 1e-8f) offset.normalize() else Vector3f(direction).negate()
        val rebound = direction.reflect(impactNormal)
        val result = HitResult(HitType.ENTITY, Vector3f(status.pos), rebound, entity)

        status.hitTargets++
        if (maxHitTargets > 0 && status.hitTargets >= maxHitTargets) {
            alive = false
        }

        effects.forEach { it.onHitEntity(entity) }
        effects.forEach { it.onHit(result) }
        onHitEntity(entity)
        onHit(result)
    }

    fun hitBlock(pos: Vector3f, faceNormal: Vector3f) {
        val rebound = currentDirection().reflect(faceNormal)
        val castPos = Vector3f(faceNormal).mul(0.01f).add(pos)
        val result = HitResult(HitType.BLOCK, castPos, rebound)

        if (status.bounces > 0) {
            status.bounces--
            val speed = status.velocity.length()
            status.velocity
                .normalize()
                .reflect(faceNormal)
                .mul(speed)
                .mul(bounceScaler.x, bounceScaler.y, bounceScaler.z)
            status.pos = castPos
        } else {
            alive = false
        }

        effects.forEach { it.onHitBlock(pos) }
        effects.forEach { it.onHit(result) }
        onHitBlock(pos)
        onHit(result)
    }

    open fun onHitEntity(entity: Entity) { }
    open fun onHitBlock(pos: Vector3f) { }
    open fun onHit(result: HitResult) { }

    override fun onSpell() { }

    override fun initLore() {
        super.initLore()
        if (status.damage > 0) lore += LoreEntry(Lore.DAMAGE, arrayOf(Lore.formatDouble(status.damage)))
    }

    override fun clone(): Projectile = super.clone() as Projectile
}
