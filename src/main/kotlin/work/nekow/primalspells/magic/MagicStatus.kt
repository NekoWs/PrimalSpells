package work.nekow.primalspells.magic

import net.minecraft.world.entity.Entity
import org.joml.Vector3f

data class MagicStatus(
    var pos: Vector3f = Vector3f(),
    var velocity: Vector3f = Vector3f(),
    var damage: Double = 0.0,
    var age: Int = 0,
    var bounces: Int = 0,
    var hitTargets: Int = 0,
    var atBlock: Boolean = false,
    val hitEntities: MutableSet<Entity> = mutableSetOf()
)