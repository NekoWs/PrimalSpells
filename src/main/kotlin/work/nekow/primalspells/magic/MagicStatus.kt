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
    val hitEntities: MutableSet<Entity> = mutableSetOf(),

    /** 爆炸伤害加成（额外伤害值） */
    var explosionDamage: Double = 0.0,

    /** 爆炸半径（传递给原版 explode 方法） */
    var explosionRadius: Float = 2.0f,

    /** 爆炸等级：0 = 不破坏方块, >=1 = 破坏方块 */
    var explosionLevel: Int = 0
)
