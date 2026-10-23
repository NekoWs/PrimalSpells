package work.nekow.primalspells.magic.effect.geometry

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/** 沿本刻飞行段短距锁定实体中心；只旋转速度，位置与命中仍由Move扫掠。 */
class Tracking(
    val range: Float,
    val strength: Float
) : BaseEffect() {
    override val phase = EffectPhase.GUIDANCE
    private var lastTarget: LivingEntity? = null

    override fun onActive() { lastTarget = null }

    override fun onTick() {
        if (!projectile.alive || !projectile.motionEnabled || !isStackPrimary) return
        val level = caster.level() as? ServerLevel ?: return
        val center = Vec3(status.pos)
        val speed = status.velocity.length().toDouble()
        if (!speed.isFinite() || speed < 1e-8) return
        // 起点附近的球形查询会漏掉两刻之间擦身而过的目标。范围仍为1格，沿真实位移段扫掠。
        val movement = Vec3(status.velocity).scale(1.0 / (1.0 + projectile.dragCoeff * speed))
        val end = center.add(movement)
        val distanceSquared = movement.lengthSqr()
        val reach = (range * stackScale).toDouble()
        fun pathDistanceSquared(entity: LivingEntity): Double {
            val offset = entity.boundingBox.center.subtract(center)
            val progress = (offset.dot(movement) / distanceSquared).coerceIn(0.0, 1.0)
            return entity.boundingBox.center.distanceToSqr(center.add(movement.scale(progress)))
        }
        fun valid(entity: LivingEntity) = entity !== caster && entity.isAlive && !entity.isRemoved &&
            !caster.isAlliedTo(entity) && !(entity is Player && (entity.isCreative || entity.isSpectator)) &&
            pathDistanceSquared(entity) <= reach * reach
        val target = lastTarget?.takeIf(::valid) ?: level.getEntitiesOfClass(LivingEntity::class.java,
            AABB(center, end).inflate(reach), ::valid)
            .minByOrNull { it.boundingBox.center.distanceToSqr(center) }
        if (target != null && target !== lastTarget) level.playSound(null, center.x, center.y, center.z,
            SoundEvents.SCULK_CLICKING, SoundSource.NEUTRAL, 0.2f, 1.6f)
        lastTarget = target
        if (target == null || status.velocity.lengthSquared() < 1e-8f) return
        val aim = target.boundingBox.center.subtract(center).toVector3f()
        if (aim.lengthSquared() < 1e-8f) return
        val heading = Vector3f(status.velocity).normalize()
        val rotation = Quaternionf().rotationTo(heading, aim.normalize())
        // strength 是每刻转向比例；用旋转而非相加加速度，不改变弹速，也不会越吸越快。
        Quaternionf().slerp(rotation, (strength * stackScale).coerceIn(0f, 1f)).transform(status.velocity)
    }
}
