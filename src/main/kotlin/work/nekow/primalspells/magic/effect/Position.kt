package work.nekow.primalspells.magic.effect

import org.joml.Vector3f

class Position(
    var pos: Vector3f
): BaseEffect() {
    override fun onActive() {
        status.pos = pos
    }
}