package work.nekow.primalspells.magic.revise

import org.joml.Vector3f
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.atan2
import kotlin.random.Random

/**
 * 球形环绕修正 —— 每个投射物以随机倾斜圆环轨道环绕施法者,
 * 多个投射物汇聚即形成球面环绕效果。
 *
 * 轨道半径 = min(当前速度 × 1.0, 5.0), 下限 1.0（每 tick 随速度动态更新）
 * 环绕角速度 = 当前速度 × 0.16, 范围 [0.04, 0.4]
 */
class SphereOrbit : Revise() {

    override val id = "sphere_orbit"

    private val radiusFactor = 1.0f
    private val radiusCap = 5.0f
    private val angularSpeedFactor = 0.16f
    private val minAngularSpeed = 0.04f
    private val maxAngularSpeed = 0.4f

    init {
        mana = 5.0
        delay = 1

        effects += object : BaseEffect() {

            private var phase = 0f

            /** 随机轨道平面的两个正交基向量 */
            private var basisU = Vector3f()
            private var basisV = Vector3f()

            override fun onActive() {
                val dx = status.pos.x - caster.x.toFloat()
                val dy = status.pos.y - (caster.y + caster.eyeHeight).toFloat()
                val dz = status.pos.z - caster.z.toFloat()
                phase = atan2(
                    Math.sqrt((dx * dx + dz * dz).toDouble()),
                    dy.toDouble()
                ).toFloat()

                val orbitNormal = randomUnitVector()
                basisU = Vector3f(orbitNormal).cross(randomUnitVector()).normalize()
                basisV = Vector3f(orbitNormal).cross(basisU).normalize()
            }

            override fun onTick() {
                if (!projectile.alive) return
                val center = caster
                val cx = center.x.toFloat()
                val cy = (center.y + center.eyeHeight).toFloat()
                val cz = center.z.toFloat()

                val currentSpeed = status.velocity.length()
                val radius = (currentSpeed * radiusFactor).coerceIn(1.0f, radiusCap)
                val angularSpeed = (currentSpeed * angularSpeedFactor).coerceIn(minAngularSpeed, maxAngularSpeed)

                phase += angularSpeed
                val cosVal = kotlin.math.cos(phase)
                val sinVal = kotlin.math.sin(phase)

                val px = cx + radius * (basisU.x * cosVal + basisV.x * sinVal)
                val py = cy + radius * (basisU.y * cosVal + basisV.y * sinVal)
                val pz = cz + radius * (basisU.z * cosVal + basisV.z * sinVal)

                status.pos.set(px, py, pz)
                status.velocity.set(
                    angularSpeed * (-basisU.x * sinVal + basisV.x * cosVal) * radius,
                    angularSpeed * (-basisU.y * sinVal + basisV.y * cosVal) * radius,
                    angularSpeed * (-basisU.z * sinVal + basisV.z * cosVal) * radius
                )
            }

            private fun randomUnitVector(): Vector3f {
                return Vector3f(
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f
                ).normalize()
            }
        }
    }
}
