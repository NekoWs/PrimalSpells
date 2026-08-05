package work.nekow.primalspells.magic

import net.minecraft.world.entity.Entity
import org.joml.Vector3f

class MagicStatus {
    var pos = Vector3f()
    var velocity = Vector3f()
    var damage: Double = 0.0
    var age: Int = 0
    val hitEntities: MutableSet<Entity> = mutableSetOf()
}