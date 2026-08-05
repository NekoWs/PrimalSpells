package work.nekow.primalspells.magic.effect

import org.joml.Vector3f

class Velocity(
    val velocity: Vector3f
): BaseEffect() {
    override fun onActive() {
        status.velocity = velocity
    }
}