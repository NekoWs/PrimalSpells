package work.nekow.primalspells.magic.effect.geometry

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.joml.Quaternionf
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 位置积分与碰撞：每 tick 先按 [dragCoeff] 施加阻尼，再把速度积分到位置，
 * 并以 1/16 格子的步长扫掠检测实体与方块碰撞。
 *
 * 阶段：[EffectPhase.MOTION] —— 这是每个 tick **唯一**推进 `status.pos` 的地方。
 * 轨道/追踪在GUIDANCE提供输入，Move扫掠实际弧线；离散跃迁才使用OVERRIDE自行命中。
 *
 * **命中语义（常被误认成 bug，实为特性）**：投射物停在实体体内时，碰撞检测每个 tick
 * 都会再次命中同一实体，命中回调可被反复触发。穿刺玩法（`Piercing` 把 `maxHitTargets`
 * 设为 `-1`，配合 `Hurt` 临时清零无敌帧）正是靠这一点实现「持续每 tick 伤害」，
 * 因此这里刻意不做「同一实体只命中一次」的去重。
 *
 * @param dragCoeff     每 tick 阻尼系数：`v *= 1 / (1 + dragCoeff * |v|)`
 * @param hitRadius     实体碰撞半径；`0` 表示不做实体检测（但仍有方块碰撞）
 * @param throughBlocks 命中方块时不反弹、不消失（继续穿行）
 */
class Move(
    val dragCoeff: Float = 0.01F,
    val hitRadius: Double = 0.0,
    val throughBlocks: Boolean = false,
) : BaseEffect() {
    override val phase = EffectPhase.MOTION
    private var leftCaster = false

    override fun onTick() {
        if (!projectile.alive || !projectile.motionEnabled) return

        val speed = status.velocity.length()
        val scale = 1 / (1 + dragCoeff * speed)
        val movement = Vector3f(status.velocity).mul(scale)
        status.velocity = Vector3f(movement)
        val startPos = Vector3f(status.pos)
        var plannedGuidance = projectile.guidance
        var path = planFlight(startPos, movement)
        val newPos = path.lastOrNull()?.position ?: Vector3f(startPos)
        projectile.flightPath += Vec3(startPos)

        if (!projectile.collisionEnabled || (throughBlocks && hitRadius <= 0.0)) {
            projectile.flightPath += path.map { Vec3(it.position) }
            path.lastOrNull()?.let { status.velocity = Vector3f(it.velocity) }
            plannedGuidance?.let { it.travelled = path.lastOrNull()?.guidanceTravel ?: it.travelled }
            status.pos = newPos
            return
        }

        val checkEntities = hitRadius > 0.0
        val level = caster.level() as? ServerLevel
        if (level != null) {
            val distance = movement.length()
            if (distance > 0f) {
                // 制导可能在穿透后换靶，宽相位覆盖本刻全部可达范围；普通弹使用实际路径包围盒。
                val sweptPath = if (plannedGuidance != null) AABB.ofSize(Vec3(startPos),
                    distance * 2.0, distance * 2.0, distance * 2.0) else AABB(
                    min(startPos.x, path.minOf { it.position.x }).toDouble(),
                    min(startPos.y, path.minOf { it.position.y }).toDouble(),
                    min(startPos.z, path.minOf { it.position.z }).toDouble(),
                    max(startPos.x, path.maxOf { it.position.x }).toDouble(),
                    max(startPos.y, path.maxOf { it.position.y }).toDouble(),
                    max(startPos.z, path.maxOf { it.position.z }).toDouble())

                // 宽相位（每个 tick 一次）：把整段位移扫掠包围盒内的实体先筛出来，
                // 子步进只在这份小候选集上做精确判定。
                // 保留逐 1/16 步进是为了命中**精确的接触点与接触面**（方块碰撞仍逐步检测），
                // 这里只把「每步一次全维度实体查询」降为「每 tick 一次」。
                // 额外外扩 1 格：容忍候选实体自身在 1 tick 内的移动，避免快速目标从快照中漏掉。
                val candidates: List<Entity> = if (checkEntities) {
                    val swept = sweptPath.inflate(hitRadius + ENTITY_SWEEP_MARGIN)
                    level.getEntities(null, swept) { projectile.canHarm(it) }
                } else {
                    emptyList()
                }

                // 球壳半径大于实体本体；斜擦球壳也必须在本刻视觉之前反射。
                val shieldHolders = if (checkEntities && projectile.maxHitTargets >= 0) {
                    val shellSweep = sweptPath.inflate(2.7)
                    level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java, shellSweep) {
                        it.isAlive && it !== caster && work.nekow.primalspells.magic.core.ShieldGuard.hasShield(it)
                    }
                } else emptyList()

                var prevPos = Vector3f(startPos)
                var index = 0
                var travelled = 0f
                while (index < path.size) {
                    val step = path[index++]
                    travelled += step.distance
                    val checkPos = step.position
                    if (plannedGuidance != null) {
                        status.velocity = Vector3f(step.velocity)
                        plannedGuidance.travelled = step.guidanceTravel
                    }
                    // 离开枪口所在的施法者碰撞箱后才允许返弹自伤，避免刚出手就撞自己。
                    if (!caster.boundingBox.inflate(hitRadius).contains(Vec3(checkPos))) leftCaster = true

                    for (holder in shieldHolders) {
                        if (work.nekow.primalspells.magic.core.ShieldGuard.interceptSegment(level, holder, projectile, prevPos, checkPos)) {
                            projectile.flightPath += projectile.shieldReflectionPoint ?: Vec3(status.pos)
                            return
                        }
                    }
                    projectile.flightPath += Vec3(checkPos)

                    if (checkEntities) {
                        val px = checkPos.x.toDouble()
                        val py = checkPos.y.toDouble()
                        val pz = checkPos.z.toDouble()
                        for (entity in candidates) {
                            if (entity === caster && !leftCaster) continue
                            // 同一检查点内也可能有重叠实体；首次命中停止后不能继续回调其它目标。
                            if (!projectile.alive || !projectile.motionEnabled) break
                            // 等价于「以检查点为中心、半边长 = hitRadius 的立方体与实体碰撞箱相交」
                            if (entity.boundingBox.inflate(hitRadius).contains(px, py, pz)) {
                                status.pos = checkPos
                                // 护盾已把弹体放回壳外并反射；不能再沿旧路径扫掠或用newPos覆盖它。
                                if (projectile.hitEntity(entity)) return
                            }
                        }
                        if (!projectile.alive || !projectile.motionEnabled) return
                    }

                    val blockPos = BlockPos(
                        floor(checkPos.x).toInt(),
                        floor(checkPos.y).toInt(),
                        floor(checkPos.z).toInt()
                    )
                    val state = level.getBlockState(blockPos)
                    val shape = state.getCollisionShape(level, blockPos)
                    if (!throughBlocks && !shape.isEmpty) {
                        val rx = (checkPos.x - blockPos.x).toDouble()
                        val ry = (checkPos.y - blockPos.y).toDouble()
                        val rz = (checkPos.z - blockPos.z).toDouble()
                        val hitAabb = shape.toAabbs().firstOrNull { aabb ->
                            rx in aabb.minX..aabb.maxX &&
                            ry in aabb.minY..aabb.maxY &&
                            rz in aabb.minZ..aabb.maxZ
                        }
                        if (hitAabb != null) {
                            val prx = (prevPos.x - blockPos.x).toDouble()
                            val pry = (prevPos.y - blockPos.y).toDouble()
                            val prz = (prevPos.z - blockPos.z).toDouble()
                            val faceNormal = computeFaceNormal(prx, pry, prz, rx, ry, rz, hitAabb)
                            if (!throughBlocks) {
                                status.pos = checkPos
                                projectile.hitBlock(checkPos, faceNormal)
                            }
                            return
                        }
                    }
                    prevPos = Vector3f(checkPos)
                    if (projectile.guidance !== plannedGuidance) {
                        // 穿透当刻换靶，保留当前切线与全速，只重排剩余路程；宽相位已覆盖任意转向。
                        plannedGuidance = projectile.guidance
                        path = planFlight(checkPos, status.velocity, (distance - travelled).coerceAtLeast(0f))
                        index = 0
                    }
                }
                status.pos = prevPos
                return
            }
        }
        status.pos = newPos
    }

    private data class FlightStep(val position: Vector3f, val velocity: Vector3f,
        val distance: Float, val guidanceTravel: Float)

    /** 保持本刻路程和速度，按曲率限幅逐子步转向；中点方向积分使前后切线连续。 */
    private fun planFlight(start: Vector3f, movement: Vector3f, distance: Float = movement.length()): List<FlightStep> {
        val speed = movement.length()
        if (!distance.isFinite() || distance <= 0f || speed <= 0f) return emptyList()
        val steps = maxOf(1, ceil(distance / (1f / 16)).toInt())
        val heading = Vector3f(movement).div(speed)
        val position = Vector3f(start)
        val guidance = projectile.guidance
        val arrival = guidance?.targetBounds?.inflate(hitRadius)
        var guidanceTravel = guidance?.travelled ?: 0f
        val blendDistance = guidance?.target?.distanceTo(Vec3(start))?.times(0.25)?.coerceIn(0.2, 1.5) ?: 1.5
        var arrived = false
        return (1..steps).map { index ->
            val step = min(index / 16f, distance) - min((index - 1) / 16f, distance)
            val next = Vector3f(heading)
            // 在真正进入目标碰撞范围后保持切线；不能把本刻剩余路径预排成绕中心的回头小圈。
            if (arrival?.contains(Vec3(position)) == true) arrived = true
            if (guidance != null && guidance.strength > 0f && !arrived) {
                val aim = guidance.target.subtract(Vec3(position)).toVector3f()
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
                        val fraction = (min(step / radius * ease, 0.2f) / angle).coerceIn(0f, 1f)
                        Quaternionf().slerp(Quaternionf().rotationTo(heading, aim), fraction).transform(next).normalize()
                    }
                }
            }
            position.add(Vector3f(heading).add(next).normalize().mul(step))
            heading.set(next)
            guidanceTravel += step
            FlightStep(Vector3f(position), Vector3f(heading).mul(speed), step, guidanceTravel)
        }
    }

    /** 用「进入 AABB 的参数 t」求碰撞面法线：取三个轴中最晚进入（最大 t）的那一轴作为面朝向。 */
    private fun computeFaceNormal(
        prevX: Double, prevY: Double, prevZ: Double,
        curX: Double, curY: Double, curZ: Double,
        aabb: AABB
    ): Vector3f {
        val dx = curX - prevX
        val dy = curY - prevY
        val dz = curZ - prevZ

        fun entryT(prev: Double, dir: Double, lo: Double, hi: Double): Double {
            if (dir == 0.0) return -1.0
            val tLo = (lo - prev) / dir
            val tHi = (hi - prev) / dir
            val tIn = minOf(tLo, tHi)
            return if (tIn in 1e-6..1.0) tIn else -1.0
        }

        val tx = entryT(prevX, dx, aabb.minX, aabb.maxX)
        val ty = entryT(prevY, dy, aabb.minY, aabb.maxY)
        val tz = entryT(prevZ, dz, aabb.minZ, aabb.maxZ)

        val maxT = maxOf(tx, ty, tz)
        val eps = 1e-5
        val nx = if (abs(tx - maxT) <= eps && tx > 0) (if (dx > 0) -1f else 1f) else 0f
        val ny = if (abs(ty - maxT) <= eps && ty > 0) (if (dy > 0) -1f else 1f) else 0f
        val nz = if (abs(tz - maxT) <= eps && tz > 0) (if (dz > 0) -1f else 1f) else 0f

        val normal = Vector3f(nx, ny, nz)
        if (normal.lengthSquared() > 0f) normal.normalize()
        return normal
    }

    private companion object {
        /**
         * 实体宽相位包围盒的额外外扩（格）：投射物扫掠盒之外再放宽一点，
         * 以容忍候选实体自己在同一 tick 内的移动。
         */
        const val ENTITY_SWEEP_MARGIN = 1.0
    }
}
