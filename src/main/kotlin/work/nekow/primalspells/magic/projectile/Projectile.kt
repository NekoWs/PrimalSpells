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

    override var cast: Int = 0

    /** 投射物本体粒子的颜色（由各投射物子类覆盖）。 */
    open val bodyColor: Color = Color.WHITE

    var maxAge: Int = 0
    var alive: Boolean = true

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
        effects += Position(position)
        effects += Velocity(velocity.mul(speed))
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

    fun hitEntity(entity: Entity) {
        val impactNormal = Vector3f(status.pos).sub(entity.position().toVector3f()).normalize()
        val rebound = Vector3f(status.velocity).normalize().reflect(impactNormal)
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
        val rebound = Vector3f(status.velocity).normalize().reflect(faceNormal)
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
