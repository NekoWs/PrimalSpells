package work.nekow.primalspells.magic.effect

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3f

class Move(val scaler: Float = 1F, val ignoreBlocks: Boolean = false) : BaseEffect() {
    override fun onTick() {
        val m = magic ?: return
        if (!m.alive) return

        val newPos = Vector3f(status.velocity).mul(scaler).add(status.pos)

        if (!ignoreBlocks) {
            val level = caster.level()
            if (level is ServerLevel) {
                val blockPos = BlockPos(newPos.x.toInt(), newPos.y.toInt(), newPos.z.toInt())
                val state = level.getBlockState(blockPos)
                if (!state.getCollisionShape(level, blockPos).isEmpty) {
                    status.pos = newPos
                    m.hitBlock(newPos)
                    m.alive = false
                    return
                }
            }
        }
        status.pos = newPos
    }
}
