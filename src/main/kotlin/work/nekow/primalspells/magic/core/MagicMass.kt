package work.nekow.primalspells.magic.core

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.projectile.Projectile

/** 魔法力的惯性质量，以普通弹/普通猪约1为基准；不是决定引力源强度的质量。 */
object MagicMass {
    const val ENTITY_TAG = "primalspells:mass"
    fun valid(value: Double, fallback: Double = 1.0): Double =
        if (value.isFinite() && value > 0.0) value.coerceIn(0.05, 1000000.0) else fallback

    /** 实体可用持久化NeoForgeData覆盖；未标注时按当前碰撞箱体积推导，大型生物更重。 */
    fun of(entity: Entity): Double {
        val box = entity.boundingBox
        val natural = valid(box.xsize * box.ysize * box.zsize / 0.729).coerceIn(0.25, 256.0)
        return valid(entity.persistentData.getDoubleOr(ENTITY_TAG, natural), natural)
    }

    /** 相同外力下加速度反比于质量；重型场源加阻尼抑制近中心穿越后积累高速。 */
    fun attract(shot: Projectile, direction: Vector3f, force: Double) {
        val inverse = 1.0 / shot.mass
        val damping = 0.04 * (1.0 - inverse.coerceAtMost(1.0))
        val radial = shot.status.velocity.dot(direction)
        shot.status.velocity.sub(Vector3f(direction).mul((radial * damping).toFloat()))
            .add(Vector3f(direction).mul((force * inverse).toFloat()))
    }
}
