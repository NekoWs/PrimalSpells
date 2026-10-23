package work.nekow.primalspells.magic.effect.geometry

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.joml.Quaternionf
import kotlin.math.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.ShieldGuard
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.toVec3
import work.nekow.primalspells.util.toPosition

/**
 * 唯一的普通位置积分器：双精度世界坐标，1/16格曲线分段，每段解析求真实接点。
 * 墙、球壳、实体按进入时间排序；反弹重排本刻剩余时间，不丢失剩余移动。
 * 实体内的重复接触仍按子步/刻回调，持续切割自行通过damageOncePerTick限频。
 */
class Move(
    val dragCoeff: Float = 0.01F,
    val hitRadius: Double = 0.0,
    val throughBlocks: Boolean = false,
) : BaseEffect() {
    override val phase = EffectPhase.MOTION
    private var leftCaster = false
    private val blockShapes = hashMapOf<BlockPos, List<AABB>>()

    private fun record(point: Vec3) {
        if (projectile.flightPath.lastOrNull()?.distanceToSqr(point)?.let { it < 1e-20 } != true) projectile.flightPath += point
    }
    private data class Impact(val contact: SweptCollision.Contact, val entity: Entity? = null, val shield: Boolean = false)

    override fun onTick() {
        if (!projectile.alive || !projectile.motionEnabled) return
        val speed = status.velocity.length()
        status.velocity.mul(1 / (1 + dragCoeff * speed))
        blockShapes.clear()
        val start = status.pos.toVec3()
        record(start)
        val level = caster.level() as? ServerLevel
        var remainingTime = 1.0
        val radius = status.velocity.length().toDouble()
        if (!radius.isFinite() || radius <= 0.0) return
        val collision = level != null && projectile.collisionEnabled && !(throughBlocks && hitRadius <= 0.0)
        // 反弹/换靶后的可达范围也必须在宽相位中，不能只查最初的直线包围盒。
        val swept = AABB.ofSize(start, radius * 2, radius * 2, radius * 2)
        val candidates = if (collision && hitRadius > 0) level!!.getEntities(null,
            swept.inflate(hitRadius + 1.0)) { projectile.canHarm(it) } else emptyList()
        val shields = if (collision && hitRadius > 0 && projectile.maxHitTargets >= 0)
            level!!.getEntitiesOfClass(LivingEntity::class.java, swept.inflate(2.7)) {
                it.isAlive && it !== caster && ShieldGuard.hasShield(it)
            } else emptyList()
        // 同一个接点换靶后不再立刻重复它；下一完整子步恢复原有持续命中语义。
        val resumeEntities = hashSetOf<Entity>()
        while (remainingTime > 1e-10 && projectile.alive && projectile.motionEnabled) {
            val movement = Vector3f(status.velocity)
            val segmentSpeed = movement.length().toDouble()
            if (segmentSpeed <= 1e-10) break
            val guidance = projectile.guidance
            val path = planFlight(status.pos.toVec3(), movement, segmentSpeed * remainingTime)
            var replan = false
            for (step in path) {
                val from = status.pos.toVec3()
                val startVelocity = Vector3f(status.velocity)
                val to = step.position
                val stepTime = step.distance / segmentSpeed
                val impacts = arrayListOf<Impact>()
                if (collision) {
                    if (!throughBlocks) blockContact(level!!, from, to)?.let { impacts += Impact(it) }
                    for (holder in shields) {
                        ShieldGuard.segmentEntry(holder, from.toPosition(), to.toPosition())?.let { point ->
                            impacts += Impact(SweptCollision.Contact(fraction(from, to, point), point, Vec3.ZERO, 0), holder, true)
                        }
                    }
                    // 必须先真正离开，再允许返弹自伤；不能在出壳子步按from重算一次出生自撞。
                    if (!caster.boundingBox.inflate(hitRadius).contains(from)) leftCaster = true
                    for (entity in candidates) {
                        if (entity in resumeEntities || entity === caster && !leftCaster || !entity.isAlive) continue
                        val box = entity.boundingBox.inflate(hitRadius)
                        val hit = if (box.contains(from)) SweptCollision.Contact(0.0, from, Vec3.ZERO, 0)
                            else SweptCollision.entry(from, to, box)
                        hit?.let { impacts += Impact(it, entity) }
                    }
                }
                // 同距先墙后盾后实体；隔着墙的实体不能抢先受到伤害。
                impacts.sortWith(compareBy<Impact> { it.contact.fraction }.thenBy { if (it.entity == null) 0 else if (it.shield) 1 else 2 }
                    .thenBy { it.entity?.id ?: 0 })
                for (impact in impacts) {
                    val hit = impact.contact
                    status.pos.set(hit.point.x, hit.point.y, hit.point.z)
                    status.velocity.set(startVelocity).lerp(step.velocity, hit.fraction.toFloat()).normalize().mul(segmentSpeed.toFloat())
                    guidance?.travelled = step.guidanceTravel - (step.distance * (1 - hit.fraction)).toFloat()
                    val interrupted = if (impact.entity == null) {
                        record(hit.point)
                        projectile.hitBlock(hit.point.toPosition(), hit.normal.toVector3f(), hit.axes)
                        true
                    } else if (impact.shield) {
                        ShieldGuard.interceptSegment(level!!, impact.entity as LivingEntity, projectile,
                            from.toPosition(), to.toPosition()).also { blocked ->
                            if (blocked) record(projectile.shieldReflectionPoint ?: hit.point)
                        }
                    } else {
                        record(hit.point)
                        projectile.hitEntity(impact.entity).also { resumeEntities += impact.entity }
                    }
                    blockShapes.clear() // 命中荷载/扩展回调可能改变地形，接下来的扫掠重新取真实形状。
                    if (!projectile.alive || !projectile.motionEnabled) return
                    if (interrupted || projectile.guidance !== guidance) {
                        remainingTime -= stepTime * hit.fraction
                        replan = true
                        break
                    }
                }
                if (replan) break
                resumeEntities.clear()
                status.pos.set(to.x, to.y, to.z)
                status.velocity.set(step.velocity)
                guidance?.travelled = step.guidanceTravel
                record(to)
                remainingTime = (remainingTime - stepTime).coerceAtLeast(0.0)
            }
            if (!replan) break
        }
    }

    private fun fraction(from: Vec3, to: Vec3, point: Vec3): Double =
        (point.subtract(from).dot(to.subtract(from)) / from.distanceToSqr(to)).coerceIn(0.0, 1.0)

    /** 碰撞形状可能越过自身方块（栅栏等），所以包含相邻格，不能只看端点所在方块。 */
    private fun blockContact(level: ServerLevel, from: Vec3, to: Vec3): SweptCollision.Contact? {
        var best: SweptCollision.Contact? = null
        for (x in floor(min(from.x, to.x)).toInt() - 1..floor(max(from.x, to.x)).toInt() + 1)
            for (y in floor(min(from.y, to.y)).toInt() - 1..floor(max(from.y, to.y)).toInt() + 1)
                for (z in floor(min(from.z, to.z)).toInt() - 1..floor(max(from.z, to.z)).toInt() + 1) {
                    val pos = BlockPos(x, y, z)
                    val boxes = blockShapes.getOrPut(pos) {
                        level.getBlockState(pos).getCollisionShape(level, pos).toAabbs().map { it.move(x.toDouble(), y.toDouble(), z.toDouble()) }
                    }
                    for (box in boxes) {
                        val hit = SweptCollision.entry(from, to, box) ?: continue
                        val old = best
                        if (old == null || hit.fraction < old.fraction - 1e-9) best = hit
                        else if (abs(hit.fraction - old.fraction) <= 1e-9) best = old.copy(
                            normal = old.normal.add(hit.normal).normalize(), axes = old.axes or hit.axes)
                    }
                }
        return best
    }

    private data class FlightStep(val position: Vec3, val velocity: Vector3f,
        val distance: Double, val guidanceTravel: Float)

    /** 保持本刻路程和速度，按曲率限幅逐子步转向；中点方向积分使前后切线连续。 */
    private fun planFlight(start: Vec3, movement: Vector3f, distance: Double = movement.length().toDouble()): List<FlightStep> {
        val speed = movement.length()
        if (!distance.isFinite() || distance <= 0.0 || speed <= 0f) return emptyList()
        val steps = maxOf(1, ceil(distance * 16).toInt())
        val heading = Vector3f(movement).div(speed)
        var position = start
        val guidance = projectile.guidance
        val arrival = guidance?.targetBounds?.inflate(hitRadius)
        var guidanceTravel = guidance?.travelled ?: 0f
        val blendDistance = guidance?.target?.distanceTo(start)?.times(0.25)?.coerceIn(0.2, 1.5) ?: 1.5
        var arrived = false
        return (1..steps).map { index ->
            val step = min(index / 16.0, distance) - min((index - 1) / 16.0, distance)
            val next = Vector3f(heading)
            // 在真正进入目标碰撞范围后保持切线；不能把本刻剩余路径预排成绕中心的回头小圈。
            if (arrival?.contains(position) == true) arrived = true
            if (guidance != null && guidance.strength > 0f && !arrived) {
                val aim = guidance.target.subtract(position).toVector3f()
                val remaining = aim.length()
                if (remaining > 1e-6f) {
                    aim.div(remaining)
                    val angle = acos(heading.dot(aim).coerceIn(-1f, 1f))
                    if (angle > 1e-6f) {
                        // 远处使用较弱制导舒展弧线，三格内渐增末段修正，避免减弱后只绕靶不命中。
                        val precision = (1f - remaining / 3f).coerceIn(0f, 1f)
                        val turnStrength = guidance.strength * (1f + precision * (2f / 3f))
                        val radius = (remaining * 0.6f).coerceIn(0.3f, 4f) / turnStrength
                        // 极多复制修正也不能在单个子步翻转180度，避免中点方向退化为零。
                        val blend = (guidanceTravel / blendDistance).coerceIn(0.0, 1.0).toFloat()
                        val ease = blend * blend * (3f - 2f * blend)
                        val fraction = (min(step / radius * ease, 0.2).toFloat() / angle).coerceIn(0f, 1f)
                        Quaternionf().slerp(Quaternionf().rotationTo(heading, aim), fraction).transform(next).normalize()
                    }
                }
            }
            position = position.add(Vec3(Vector3f(heading).add(next).normalize()).scale(step))
            heading.set(next)
            guidanceTravel += step.toFloat()
            FlightStep(position, Vector3f(heading).mul(speed), step, guidanceTravel)
        }
    }

}
