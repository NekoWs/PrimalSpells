package work.nekow.primalspells.magic.effect.geometry

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.toVec3

/** 沿预计飞行段锁定实体中心；只声明目标与转向强度，由Move连续转弯和扫掠命中。 */
class Tracking(
    val range: Float,
    val strength: Float
) : BaseEffect() {
    override val phase = EffectPhase.GUIDANCE
    private var lastTarget: LivingEntity? = null
    private var activeGuidance: work.nekow.primalspells.magic.projectile.Projectile.Guidance? = null
    // 仅跳过制导选靶，不去重真实碰撞/帧伤；方块反弹后允许重新制导。
    private var passedTargets = hashSetOf<java.util.UUID>()

    override fun onActive() { lastTarget = null; activeGuidance = null; passedTargets = hashSetOf() }

    override fun onHitEntity(target: Entity) {
        if (!isStackPrimary) return
        passedTargets += target.uuid
        if (target === lastTarget && projectile.alive) {
            lastTarget = null; activeGuidance = null; projectile.guidance = null
            // 高速穿透不能等到下一刻再换靶；Move会从接触切线重排尚未完成的路程。
            onTick()
        }
    }

    override fun onHitBlock(pos: Vector3d) {
        passedTargets.clear(); lastTarget = null; activeGuidance = null; projectile.guidance = null
        onTick()
    }

    override fun onTick() {
        if (!projectile.alive || !projectile.motionEnabled || !isStackPrimary) return
        val level = caster.level() as? ServerLevel ?: return
        val center = status.pos.toVec3()
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
        fun alignment(entity: LivingEntity): Double {
            val offset = entity.boundingBox.center.subtract(center)
            return if (offset.lengthSqr() < 1e-10) 1.0 else offset.dot(movement) / (offset.length() * movement.length())
        }
        fun valid(entity: LivingEntity) = entity !== caster && entity.uuid !in passedTargets && entity.isAlive && !entity.isRemoved &&
            !caster.isAlliedTo(entity) && !(entity is Player && (entity.isCreative || entity.isSpectator)) &&
            alignment(entity) >= -1e-6 &&
            pathDistanceSquared(entity) <= reach * reach
        val target = lastTarget?.takeIf(::valid) ?: level.getEntitiesOfClass(LivingEntity::class.java,
            AABB(center, end).inflate(reach), ::valid)
            .minWithOrNull(compareByDescending<LivingEntity> { alignment(it) }
                .thenBy { pathDistanceSquared(it) }.thenBy { it.boundingBox.center.distanceToSqr(center) })
        if (target != null && target !== lastTarget) level.playSound(null, center.x, center.y, center.z,
            SoundEvents.SCULK_CLICKING, SoundSource.NEUTRAL, 0.2f, 1.6f)
        val travelled = if (target === lastTarget) activeGuidance?.travelled ?: 0f else 0f
        lastTarget = target
        activeGuidance = target?.let { work.nekow.primalspells.magic.projectile.Projectile.Guidance(
            it.boundingBox.center, (strength * stackScale).coerceAtLeast(0f), it.boundingBox, travelled) }
        projectile.guidance = activeGuidance
    }
}
