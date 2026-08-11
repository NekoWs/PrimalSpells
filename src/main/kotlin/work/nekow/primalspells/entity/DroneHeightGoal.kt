package work.nekow.primalspells.entity

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class DroneHeightGoal(
    private val drone: DroneEntity
) : Goal() {

    override fun canUse(): Boolean = true

    override fun tick() {
        val level = drone.level()
        val pos = drone.blockPosition()
        val groundY = findGroundY(level, pos)
        if (groundY == Double.NEGATIVE_INFINITY) return

        val height = drone.y - groundY
        val force = when {
            height > 8.0 -> -0.08
            height < 2.0 ->  0.08
            height > 5.0 -> -0.02
            height < 3.0 ->  0.02
            else -> 0.0
        }
        if (force != 0.0) {
            drone.addDeltaMovement(Vec3(0.0, force, 0.0))
        }
    }

    private fun findGroundY(level: Level, pos: BlockPos): Double {
        for (dy in 0..8) {
            val cy = pos.y - dy
            if (cy < level.minY) return Double.NEGATIVE_INFINITY
            if (!level.getBlockState(BlockPos(pos.x, cy, pos.z)).isAir) return cy + 1.0
        }
        return Double.NEGATIVE_INFINITY
    }

    override fun requiresUpdateEveryTick(): Boolean = true
}
