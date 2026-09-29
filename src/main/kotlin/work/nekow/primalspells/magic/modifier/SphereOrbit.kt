package work.nekow.primalspells.magic.modifier

import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 球形环绕修正 —— 每个投射物以随机倾斜的圆环轨道环绕施法者，多个投射物汇聚即形成球面环绕。
 *
 * 轨道半径 = min(当前速度 × 1.0, 5.0)，下限 1.0；环绕角速度 = 当前速度 × 0.16，范围 [0.04, 0.4]。
 *
 * 与 [SelfOrbit] 同理，注入的效果声明为 [EffectPhase.OVERRIDE]：在 `Move` 积分之后绝对定位，
 * 因此轨道不会被积分位移叠加而漂移。
 */
class SphereOrbit : Modifier() {

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
            override val phase = EffectPhase.OVERRIDE

            /** 轨道相位（弧度） */
            private var orbitPhase = 0f

            /** 随机轨道平面的两个正交基向量 */
            private var basisU = Vector3f()
            private var basisV = Vector3f()

            override fun onActive() {
                val dx = status.pos.x - caster.x.toFloat()
                val dy = status.pos.y - (caster.y + caster.eyeHeight).toFloat()
                val dz = status.pos.z - caster.z.toFloat()
                orbitPhase = atan2(sqrt((dx * dx + dz * dz).toDouble()), dy.toDouble()).toFloat()

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

                orbitPhase += angularSpeed
                val cosVal = cos(orbitPhase)
                val sinVal = sin(orbitPhase)

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
