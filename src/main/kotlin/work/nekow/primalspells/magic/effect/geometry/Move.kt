package work.nekow.primalspells.magic.effect.geometry

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlin.math.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.lifecycle.ShieldGuard
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.util.toVec3

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
    private val blockShapes = Long2ObjectOpenHashMap<List<AABB>>()
    private val loadedChunks = Long2ObjectOpenHashMap<LevelChunk>()
    private var collisionAreaLoaded = true
    private val nearbyBoxes = arrayListOf<AABB>()
    private var neighborhoodValid = false
    private var neighborhoodLow = 0L
    private var neighborhoodHigh = 0L
    private val impacts = arrayListOf<Impact>()
    private val resumeEntities = hashSetOf<Entity>()
    private class Collider(val entity: Entity, var original: AABB, var box: AABB)

    private fun record(point: Vec3) {
        if (projectile.flightPath.lastOrNull()?.distanceToSqr(point)?.let { it < 1e-20 } != true) projectile.flightPath += point
    }
    private data class Impact(val contact: SweptCollision.Contact, val entity: Entity? = null, val shield: Boolean = false)

    override fun onTick() {
        if (!projectile.alive || !projectile.motionEnabled) return
        val speed = status.velocity.length()
        status.velocity.mul(1 / (1 + dragCoeff * speed))
        blockShapes.clear()
        loadedChunks.clear()
        collisionAreaLoaded = true
        neighborhoodValid = false
        resumeEntities.clear()
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
            swept.inflate(hitRadius + 1.0)) { projectile.canHitEntity(it) }
            .map { Collider(it, it.boundingBox, it.boundingBox.inflate(hitRadius)) } else emptyList()
        val shields = if (collision && hitRadius > 0 && projectile.maxHitTargets >= 0)
            level!!.getEntitiesOfClass(LivingEntity::class.java, swept.inflate(2.7)) {
                projectile.canHitEntity(it) && it !== caster && ShieldGuard.hasShield(it)
            } else emptyList()
        // 同一个接点换靶后不再立刻重复它；下一完整子步恢复原有持续命中语义。
        val startVelocity = Vector3f()
        while (remainingTime > 1e-10 && projectile.alive && projectile.motionEnabled) {
            val movement = Vector3f(status.velocity)
            val segmentSpeed = movement.length().toDouble()
            if (segmentSpeed <= 1e-10) break
            val guidance = projectile.guidance
            val path = FlightPlan(status.pos.toVec3(), movement, segmentSpeed * remainingTime)
            var replan = false
            while (path.advance()) {
                val step = path
                val from = status.pos.toVec3()
                startVelocity.set(status.velocity)
                val to = step.position
                val stepTime = step.distance / segmentSpeed
                impacts.clear()
                if (collision) {
                    if (!throughBlocks) {
                        blockContact(level!!, from, to)?.let { impacts += Impact(it) }
                        // 未知地形不当成空气，也不当作碰撞；留在最后的已知位置，寿命照常推进。
                        if (!collisionAreaLoaded) return
                    }
                    for (holder in shields) {
                        ShieldGuard.segmentEntry(holder, from.toPosition(), to.toPosition())?.let { point ->
                            impacts += Impact(SweptCollision.Contact(fraction(from, to, point), point, Vec3.ZERO, 0), holder, true)
                        }
                    }
                    // 必须先真正离开，再允许返弹自伤；不能在出壳子步按from重算一次出生自撞。
                    if (!leftCaster && !caster.boundingBox.inflate(hitRadius).contains(from)) leftCaster = true
                    for (candidate in candidates) {
                        val entity = candidate.entity
                        if (entity in resumeEntities || entity === caster && !leftCaster || !projectile.canHitEntity(entity)) continue
                        val currentBox = entity.boundingBox
                        if (currentBox !== candidate.original) {
                            candidate.original = currentBox; candidate.box = currentBox.inflate(hitRadius)
                        }
                        val box = candidate.box
                        val hit = if (box.contains(from)) SweptCollision.Contact(0.0, from, Vec3.ZERO, 0)
                            else SweptCollision.entry(from, to, box)
                        hit?.let { impacts += Impact(it, entity) }
                    }
                }
                // 同距先墙后盾后实体；隔着墙的实体不能抢先受到伤害。
                if (impacts.size > 1) impacts.sortWith(IMPACT_ORDER)
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
                    blockShapes.clear(); neighborhoodValid = false // 命中回调可能改变地形，下个子步重新取形状。
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
        val minX = floor(min(from.x, to.x)).toInt() - 1
        val minY = floor(min(from.y, to.y)).toInt() - 1
        val minZ = floor(min(from.z, to.z)).toInt() - 1
        val maxX = floor(max(from.x, to.x)).toInt() + 1
        val maxY = floor(max(from.y, to.y)).toInt() + 1
        val maxZ = floor(max(from.z, to.z)).toInt() + 1
        val low = BlockPos.asLong(minX, minY, minZ)
        val high = BlockPos.asLong(maxX, maxY, maxZ)
        // 相邻子步通常还在同一格，只有跨格/命中回调才重收集邻格；不降低扫掠精度。
        if (!neighborhoodValid || low != neighborhoodLow || high != neighborhoodHigh) {
            neighborhoodLow = low; neighborhoodHigh = high; neighborhoodValid = true
            nearbyBoxes.clear()
            for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
                // 世界高度外是已知虚空，不查询区块；也不把超出打包范围的Y写入坐标缓存。
                if (level.isOutsideBuildHeight(y)) continue
                val key = BlockPos.asLong(x, y, z)
                var boxes = blockShapes.get(key)
                if (boxes == null) {
                    val pos = BlockPos(x, y, z)
                    val chunkKey = ChunkPos.pack(x shr 4, z shr 4)
                    var chunk = loadedChunks.get(chunkKey)
                    if (chunk == null) {
                        chunk = level.chunkSource.getChunkNow(x shr 4, z shr 4)
                        if (chunk == null) {
                            collisionAreaLoaded = false; neighborhoodValid = false; nearbyBoxes.clear()
                            return null
                        }
                        loadedChunks.put(chunkKey, chunk)
                    }
                    val shape = chunk.getBlockState(pos).getCollisionShape(level, pos)
                    boxes = if (shape.isEmpty) emptyList() else shape.toAabbs()
                        .map { it.move(x.toDouble(), y.toDouble(), z.toDouble()) }
                    blockShapes.put(key, boxes)
                }
                // 空列表的addAll也可能分配临时数组；空气格是最常见情况，直接跳过。
                if (boxes.isNotEmpty()) nearbyBoxes.addAll(boxes)
            }
        }
        var best: SweptCollision.Contact? = null
        for (box in nearbyBoxes) {
            val hit = SweptCollision.entry(from, to, box) ?: continue
            val old = best
            if (old == null || hit.fraction < old.fraction - 1e-9) best = hit
            else if (abs(hit.fraction - old.fraction) <= 1e-9) best = old.copy(
                normal = old.normal.add(hit.normal).normalize(), axes = old.axes or hit.axes)
        }
        return best
    }

    private companion object {
        val IMPACT_ORDER = compareBy<Impact> { it.contact.fraction }
            .thenBy { if (it.entity == null) 0 else if (it.shield) 1 else 2 }.thenBy { it.entity?.id ?: 0 }
    }

    /** 按需积分下一子步；反弹/换靶只舍弃规划器，未行驶的未来路径不分配也不计算。 */
    private inner class FlightPlan(start: Vec3, movement: Vector3f, totalDistance: Double) {
        private val speed = movement.length()
        private val heading = Vector3f(movement).div(speed)
        private val next = Vector3f()
        private val chord = Vector3f()
        private val aim = Vector3f()
        private val turn = Quaternionf()
        private val targetTurn = Quaternionf()
        private val guidance = projectile.guidance
        private val arrival = guidance?.targetBounds?.inflate(hitRadius)
        private val blendDistance = guidance?.target?.distanceTo(start)?.times(0.25)?.coerceIn(0.2, 1.5) ?: 1.5
        private var arrived = false
        private var remainingDistance = totalDistance
        var position: Vec3 = start
            private set
        val velocity = Vector3f(movement)
        var distance = 0.0
            private set
        var guidanceTravel = guidance?.travelled ?: 0f
            private set

        fun advance(): Boolean {
            if (remainingDistance <= 1e-12 || !remainingDistance.isFinite()) return false
            distance = min(1.0 / 16, remainingDistance)
            remainingDistance -= distance
            next.set(heading)
            if (arrival?.contains(position) == true) arrived = true
            if (guidance != null && guidance.strength > 0f && !arrived) {
                aim.set((guidance.target.x - position.x).toFloat(), (guidance.target.y - position.y).toFloat(),
                    (guidance.target.z - position.z).toFloat())
                val remaining = aim.length()
                if (remaining > 1e-6f) {
                    aim.div(remaining)
                    val angle = acos(heading.dot(aim).coerceIn(-1f, 1f))
                    if (angle > 1e-6f) {
                        val precision = (1f - remaining / 3f).coerceIn(0f, 1f)
                        val turnStrength = guidance.strength * (1f + precision * (2f / 3f))
                        val radius = (remaining * 0.6f).coerceIn(0.3f, 4f) / turnStrength
                        val blend = (guidanceTravel / blendDistance).coerceIn(0.0, 1.0).toFloat()
                        val ease = blend * blend * (3f - 2f * blend)
                        val fraction = (min(distance / radius * ease, 0.2).toFloat() / angle).coerceIn(0f, 1f)
                        turn.identity().slerp(targetTurn.rotationTo(heading, aim), fraction).transform(next).normalize()
                    }
                }
            }
            chord.set(heading).add(next).normalize()
            position = position.add(chord.x * distance, chord.y * distance, chord.z * distance)
            heading.set(next)
            guidanceTravel += distance.toFloat()
            velocity.set(heading).mul(speed)
            return true
        }
    }
}
