package work.nekow.primalspells.entity

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * 无人机高度维持目标 —— 使无人机保持在地面以上 2～8 格范围内活动。
 * 仅直接修改实体速度，不占用 AI 移动标志，确保漫游和攻击目标可正常运行。
 */
class DroneHeightGoal(
    private val drone: DroneEntity
) : Goal() {

    override fun canUse(): Boolean = true // 始终运行

    override fun tick() {
        val level = drone.level()
        val y = drone.y

        // 向下搜索地面
        val groundY = findGroundY(level, drone.blockPosition())

        if (groundY == Double.NEGATIVE_INFINITY) return // 无法找到地面

        val height = y - groundY // 当前离地高度

        val desiredHeight = 4.0 // 目标悬停高度（2～8 中间值）
        val threshold = 1 // 容许误差

        if (height > 8.0) {
            // 过高：向下推
            drone.addDeltaMovement(Vec3(0.0, -0.08, 0.0))
        } else if (height < 2.0) {
            // 过低：向上推
            drone.addDeltaMovement(Vec3(0.0, 0.08, 0.0))
        } else if (height > desiredHeight + threshold) {
            // 略高：轻微下推
            drone.addDeltaMovement(Vec3(0.0, -0.03, 0.0))
        } else if (height < desiredHeight - threshold) {
            // 略低：轻微上推
            drone.addDeltaMovement(Vec3(0.0, 0.03, 0.0))
        }
        // 在目标高度 ±1 内不施力，随惯性漂移
    }

    /**
     * 从当前位置向下扫描，找到第一个非空气方块的 Y 坐标。
     * 返回该方块顶面坐标，若未找到返回负无穷。
     */
    private fun findGroundY(level: Level, startPos: BlockPos): Double {
        val maxScan = 16 // 最大向下扫描深度
        for (dy in 0..maxScan) {
            val checkY = startPos.y - dy
            if (checkY < level.minY) return Double.NEGATIVE_INFINITY
            val state: BlockState = level.getBlockState(BlockPos(startPos.x, checkY, startPos.z))
            if (!state.isAir) {
                return checkY + 1.0 // 返回方块顶面坐标
            }
            // 同时检查下方是否为实体方块（更可靠的地面检测）
            val below: BlockState = level.getBlockState(BlockPos(startPos.x, checkY - 1, startPos.z))
            if (below.isCollisionShapeFullBlock(level, BlockPos(startPos.x, checkY - 1, startPos.z))) {
                return checkY.toDouble()
            }
        }
        return Double.NEGATIVE_INFINITY
    }

    override fun requiresUpdateEveryTick(): Boolean = true
}
