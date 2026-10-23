package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.visual.PixelVfx
import work.nekow.primalspells.util.toVec3
import java.util.ArrayDeque
import java.util.UUID

/** Noita式分段魔弹：慢漂移，第4/7/10/14刻寻敌跃迁；无目标前跳，只检查落点地形，避开最近两次目标。 */
class ChainArc : BaseEffect(), DirectDamage {
    override val phase = EffectPhase.OVERRIDE
    override val damageStrength: Double get() = status.damage
    private var level: ServerLevel? = null
    private val recent = ArrayDeque<UUID>()
    private var nextJumpAge = jumpAge(1)
    /** 本次施法已发生的跃迁/实际命中次数，供实机场景读取。 */
    var jumpsMade = 0
        private set
    var hitsLanded = 0
        private set

    override fun onActive() {
        level = caster.level() as? ServerLevel
        // 速度卡仍先修改初速；保留同一比例，让运动卡、外部受力也能改变后续搜索范围。
        status.velocity.mul(DRIFT_RATIO)
    }

    override fun onHitEntity(target: Entity) {
        val world = level ?: return
        if (SpellDamage.hit(world, target, projectile, status.damage)) hitsLanded++
    }

    override fun onTick() {
        val world = level ?: return
        if (!projectile.alive) return
        if (caster.level() !== world) { projectile.alive = false; return }
        if (status.age < nextJumpAge || status.age >= projectile.maxAge) return
        jumpsMade++
        nextJumpAge = jumpAge(jumpsMade + 1)
        val start = status.pos.toVec3()
        val driveSpeed = status.velocity.length().toDouble() / DRIFT_RATIO
        val radius = (driveSpeed * 4.0).coerceAtLeast(1.0) * projectile.areaScale
        val target = world.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(start, radius*2, radius*2, radius*2)) {
            it.isAlive && projectile.canHarm(it) && (it === caster || !caster.isAlliedTo(it)) && it.uuid !in recent &&
                it.boundingBox.center.distanceToSqr(start) <= radius*radius
        }.minByOrNull { it.id }
        val heading = if (status.velocity.lengthSquared() > 1e-8f) Vec3(status.velocity).normalize() else projectile.launchDirection
        val end = target?.boundingBox?.center ?: start.add(heading.scale(radius * 0.625))
        status.pos.set(end.x, end.y, end.z)
        PixelVfx.arc(ParticleManager.of(world), start, end)
        SpellInteractions.beam(projectile, start, end, MagicManager.projectiles.toList())
        val block = BlockPos.containing(end)
        if (!world.getBlockState(block).getCollisionShape(world, block).isEmpty && !projectile.throughBlocks) {
            projectile.hitBlock(Vector3d(status.pos), Vector3f(heading.x.toFloat(),heading.y.toFloat(),heading.z.toFloat()).negate())
            return
        }
        if (target != null) {
            recent.addLast(target.uuid)
            if (recent.size > 2) recent.removeFirst()
            // 临时来向用于护盾和击退；普通移动保留低速漂移，不把瞬移长度当作持续速度。
            val drift = Vector3f(status.velocity)
            val incoming = end.subtract(start).let { if (it.lengthSqr() > 1e-8) it else heading }
            status.velocity.set(incoming.x.toFloat(), incoming.y.toFloat(), incoming.z.toFloat())
            // 使用公共命中分派，让爆炸、复制等命中修正也在真实落点生效。
            if (projectile.hitEntity(target)) {
                // 护盾反射了跃迁来向；保留反射方向，只恢复原有慢漂移速率。
                status.velocity.normalize().mul(drift.length())
            } else status.velocity.set(drift)
        }
    }

    companion object {
        /** Noita每10帧（60Hz）跃迁；按20Hz向上取整，避免逐跳舍入累计偏差。 */
        fun jumpAge(index: Int): Int = (index * 10 + 2) / 3
        const val JUMP_INTERVAL = 10.0 / 3.0
        const val DRIFT_RATIO = 0.025f
    }
}
