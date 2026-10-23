package work.nekow.primalspells.util

import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3f

data class HitResult(
    val type: HitType,
    val pos: Vector3d,
    val normal: Vector3f,
    val entity: Entity? = null
)

enum class HitType { ENTITY, BLOCK }
