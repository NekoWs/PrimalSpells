package work.nekow.primalspells.magic.effect

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class Move(
    val dragCoeff: Float = 0.01F,
    val ignoreBlocks: Boolean = false,
    val hitRadius: Double = 0.0,
    val throughBlocks: Boolean = false,
    val selfDamage: Boolean = false,
) : BaseEffect() {
    override fun onTick() {
        if (!projectile.alive) return

        val speed = status.velocity.length()
        val scale = 1 / (1 + dragCoeff * speed)
        val movement = Vector3f(status.velocity).mul(scale)
        status.velocity = Vector3f(movement)
        val newPos = Vector3f(movement).add(status.pos)

        val checkBlocks = !ignoreBlocks
        val checkEntities = hitRadius > 0.0

        if (checkBlocks || checkEntities) {
            val level = caster.level()
            if (level is ServerLevel) {
                val distance = movement.length()
                if (distance > 0f) {
                    val stepVec = Vector3f(movement).div(distance)
                    val stepSize = 1F / 16
                    val steps = maxOf(1, ceil(distance / stepSize).toInt())
                    var prevPos = Vector3f(status.pos)
                    for (i in 1..steps) {
                        val t = min(i * stepSize, distance)
                        val checkPos = Vector3f(stepVec).mul(t).add(status.pos)

                        if (checkEntities) {
                            val center = Vec3(checkPos.x.toDouble(), checkPos.y.toDouble(), checkPos.z.toDouble())
                            val aabb = AABB.ofSize(center, hitRadius * 2.0, hitRadius * 2.0, hitRadius * 2.0)
                            level.getEntities(null, aabb) { it != caster || selfDamage }.forEach { entity ->
                                status.hitEntities.add(entity)
                                status.pos = checkPos
                                projectile.hitEntity(entity)
                            }
                            if (!projectile.alive) return
                        }

                        if (checkBlocks) {
                            val blockPos = BlockPos(
                                floor(checkPos.x).toInt(),
                                floor(checkPos.y).toInt(),
                                floor(checkPos.z).toInt()
                            )
                            val state = level.getBlockState(blockPos)
                            val shape = state.getCollisionShape(level, blockPos)
                            if (!shape.isEmpty) {
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
                                    if (throughBlocks) {
                                        status.atBlock = true
                                    } else {
                                        status.pos = checkPos
                                        projectile.hitBlock(checkPos, faceNormal)
                                    }
                                    return
                                } else {
                                    status.atBlock = false
                                }
                            }
                        }
                        prevPos = Vector3f(checkPos)
                    }
                }
            }
        }
        status.pos = newPos
    }

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
}
