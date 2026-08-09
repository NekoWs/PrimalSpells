package work.nekow.primalspells.utils

import net.minecraft.world.entity.Entity
import org.joml.Vector3f

data class HitResult(
    val type: HitType,
    val pos: Vector3f,
    val normal: Vector3f,
    val entity: Entity? = null
)

enum class HitType { ENTITY, BLOCK }
