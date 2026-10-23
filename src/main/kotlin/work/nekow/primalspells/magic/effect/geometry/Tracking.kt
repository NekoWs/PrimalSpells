package work.nekow.primalspells.magic.effect.geometry

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/** 沿预计飞行段锁定实体中心；只声明目标与转向强度，由Move连续转弯和扫掠命中。 */
class Tracking(
    val range: Float,
    val strength: Float
) : BaseEffect() {
    override val phase = EffectPhase.GUIDANCE
    private var lastTarget: LivingEntity? = null
    // 仅跳过制导选靶，不去重真实碰撞/帧伤；方块反弹后允许重新制导。
    private var passedTargets = hashSetOf<java.util.UUID>()

    override fun onActive() { lastTarget = null; passedTargets = hashSetOf() }

    override fun onHitEntity(target: Entity) {
        passedTargets += target.uuid
        if (target === lastTarget && projectile.alive) { lastTarget = null; projectile.guidance = null }
    }

    override fun onHitBlock(pos: org.joml.Vector3f) { passedTargets.clear() }

    override fun onTick() {
        if (!projectile.alive || !projectile.motionEnabled || !isStackPrimary) return
        val level = caster.level() as? ServerLevel ?: return
        val center = Vec3(status.pos)
        val speed = status.velocity.length().toDouble()
        if (!speed.isFinite() || speed < 1e-8) return
        // 高速弹不能只查刻起点；沿预计位移段寻找目标，已锁定目标优先保留。
        val movement = Vec3(status.velocity).scale(1.0 / (1.0 + projectile.dragCoeff * speed))
        val end = center.add(movement)
        val distanceSquared = movement.lengthSqr()
        val reach = (range * stackScale).toDouble()
        fun pathDistanceSquared(entity: LivingEntity): Double {
            val offset = entity.boundingBox.center.subtract(center)
            val progress = (offset.dot(movement) / distanceSquared).coerceIn(0.0, 1.0)
            return entity.boundingBox.center.distanceToSqr(center.add(movement.scale(progress)))
        }
        fun valid(entity: LivingEntity) = entity !== caster && entity.uuid !in passedTargets && entity.isAlive && !entity.isRemoved &&
            !caster.isAlliedTo(entity) && !(entity is Player && (entity.isCreative || entity.isSpectator)) &&
            pathDistanceSquared(entity) <= reach * reach
        val target = lastTarget?.takeIf(::valid) ?: level.getEntitiesOfClass(LivingEntity::class.java,
            AABB(center, end).inflate(reach), ::valid)
            .minByOrNull { it.boundingBox.center.distanceToSqr(center) }
        if (target != null && target !== lastTarget) level.playSound(null, center.x, center.y, center.z,
            SoundEvents.SCULK_CLICKING, SoundSource.NEUTRAL, 0.2f, 1.6f)
        lastTarget = target
        if (target == null) return
        projectile.guidance = work.nekow.primalspells.magic.projectile.Projectile.Guidance(
            target.boundingBox.center, (strength * stackScale).coerceAtLeast(0f), target.boundingBox)
    }
}
