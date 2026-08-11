package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.atan2

/**
 * 周身环绕修正 —— 投射物射出后在施法者周围水平面（XZ）上做圆周运动。
 *
 * 轨道半径 = min(当前速度 × 1.0, 5.0), 下限 1.0（每 tick 随速度动态更新）
 * 环绕角速度 = 当前速度 × 0.16, 范围 [0.04, 0.4]
 */
class SelfOrbit : Revise() {

    override val id = "self_orbit"

    /** 半径比例系数 */
    private val radiusFactor = 1.0f

    /** 半径上限 */
    private val radiusCap = 5.0f

    /** 角速度比例系数（角速度 = 当前速度 × 此值） */
    private val angularSpeedFactor = 0.16f

    /** 最小角速度 */
    private val minAngularSpeed = 0.04f

    /** 最大角速度 */
    private val maxAngularSpeed = 0.4f

    init {
        mana = 4.0
        delay = 1

        effects += object : BaseEffect() {

            private var phase = 0f

            override fun onActive() {
                val dx = status.pos.x - caster.x.toFloat()
                val dz = status.pos.z - caster.z.toFloat()
                phase = atan2(dz.toDouble(), dx.toDouble()).toFloat()
            }

            override fun onTick() {
                if (!projectile.alive) return
                val center = caster
                val cx = center.x.toFloat()
                val cy = (center.y + center.eyeHeight).toFloat()
                val cz = center.z.toFloat()

                val currentSpeed = status.velocity.length()

                // 轨道半径随当前速度动态更新
                val radius = (currentSpeed * radiusFactor).coerceIn(1.0f, radiusCap)

                // 角速度随当前速度动态更新
                val angularSpeed = (currentSpeed * angularSpeedFactor).coerceIn(minAngularSpeed, maxAngularSpeed)

                phase += angularSpeed

                val px = cx + radius * kotlin.math.cos(phase)
                val pz = cz + radius * kotlin.math.sin(phase)

                status.pos.set(px, cy, pz)
                status.velocity.set(
                    -radius * angularSpeed * kotlin.math.sin(phase),
                    0f,
                    radius * angularSpeed * kotlin.math.cos(phase)
                )
            }
        }
    }
}
