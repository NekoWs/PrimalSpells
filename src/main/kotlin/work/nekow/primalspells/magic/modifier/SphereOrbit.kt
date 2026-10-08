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

/** 环绕半径=2+驱动速度×2（最多12格）；角速度=驱动速度×0.6/半径。
 * 在GUIDANCE阶段给出位移，实体/地形命中仍完全由Move扫掠处理。
 */
class SphereOrbit : Modifier() {

    override val id = "sphere_orbit"

    private val radiusFactor = 2.0f
    private val radiusCap = 12.0f
    private val maxAngularSpeed = 0.4f

    init {
        mana = 5.0
        delay = 0

        effects += object : BaseEffect() {
            override val phase = EffectPhase.GUIDANCE

            /** 轨道相位（弧度） */
            private var orbitPhase = 0f
            private var driveSpeed = 0f
            private var previousTravel = 0f

            /** 随机轨道平面的两个正交基向量 */
            private var basisU = Vector3f()
            private var basisV = Vector3f()

            override fun onActive() {
                driveSpeed = status.velocity.length()
                previousTravel = driveSpeed
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

                // 与上一段实际命令比较，只继承阻尼/加减速倍率，不把较短的弦长当成速度衰减。
                if (previousTravel > 1e-6f) driveSpeed *= status.velocity.length() / previousTravel
                val currentSpeed = driveSpeed
                val radius = (2.0f + currentSpeed * radiusFactor).coerceIn(2.0f, radiusCap)
                val angularSpeed = (currentSpeed * 0.6f / radius).coerceIn(0.0f, maxAngularSpeed)

                orbitPhase += angularSpeed
                val cosVal = cos(orbitPhase)
                val sinVal = sin(orbitPhase)

                val px = cx + radius * (basisU.x * cosVal + basisV.x * sinVal)
                val py = cy + radius * (basisU.y * cosVal + basisV.y * sinVal)
                val pz = cz + radius * (basisU.z * cosVal + basisV.z * sinVal)

                val delta = Vector3f(px, py, pz).sub(status.pos)
                if (delta.length() > currentSpeed && currentSpeed > 0f) delta.normalize().mul(currentSpeed)
                status.velocity.set(delta)
                previousTravel = delta.length()

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
