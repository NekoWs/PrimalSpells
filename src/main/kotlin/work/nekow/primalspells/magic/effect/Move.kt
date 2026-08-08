package work.nekow.primalspells.magic.effect

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3f
import kotlin.math.floor
import kotlin.math.min

class Move(val scaler: Float = 1F, val ignoreBlocks: Boolean = false) : BaseEffect() {
    override fun onTick() {
        if (!projectile.alive) return

        val movement = Vector3f(status.velocity).mul(scaler)
        val newPos = Vector3f(movement).add(status.pos)

        if (!ignoreBlocks) {
            val level = caster.level()
            if (level is ServerLevel) {
                val distance = movement.length()
                if (distance > 0f) {
                    val stepVec = Vector3f(movement).div(distance)
                    val stepSize = 1F / 16
                    val steps = maxOf(1, (distance / stepSize).toInt())
                    for (i in 1..steps) {
                        val t = min(i * stepSize, distance)
                        val checkPos = Vector3f(stepVec).mul(t).add(status.pos)
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
                            val hit = shape.toAabbs().any { aabb ->
                                rx in aabb.minX..aabb.maxX &&
                                ry in aabb.minY..aabb.maxY &&
                                rz in aabb.minZ..aabb.maxZ
                            }
                            if (hit) {
                                status.pos = checkPos
                                projectile.hitBlock(checkPos)
                                projectile.alive = false
                                return
                            }
                        }
                    }
                }
            }
        }
        status.pos = newPos
    }
}
