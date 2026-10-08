package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 环绕半径=2+驱动速度×2（最多12格）；角速度=驱动速度×0.6/半径。
 * 在GUIDANCE阶段给出位移，实体/地形命中仍完全由Move扫掠处理。
 */
class SelfOrbit : Modifier() {

    override val id = "self_orbit"

    /** 半径比例系数 */
    private val radiusFactor = 2.0f

    /** 半径上限 */
    private val radiusCap = 12.0f

    /** 最大角速度 */
    private val maxAngularSpeed = 0.4f

    init {
        mana = 4.0
        delay = 0

        effects += object : BaseEffect() {
            override val phase = EffectPhase.GUIDANCE

            /** 轨道相位与独立驱动速度，避免弦长反馈使半径逐渐坍缩。 */
            private var orbitPhase = 0f
            private var driveSpeed = 0f
            private var previousTravel = 0f

            override fun onActive() {
                driveSpeed = status.velocity.length()
                previousTravel = driveSpeed
                val dx = status.pos.x - caster.x.toFloat()
                val dz = status.pos.z - caster.z.toFloat()
                orbitPhase = atan2(dz.toDouble(), dx.toDouble()).toFloat()
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

                // 轨道半径随当前速度动态更新
                val radius = (2.0f + currentSpeed * radiusFactor).coerceIn(2.0f, radiusCap)

                // 角速度随当前速度动态更新
                val angularSpeed = (currentSpeed * 0.6f / radius).coerceIn(0.0f, maxAngularSpeed)

                orbitPhase += angularSpeed

                val px = cx + radius * cos(orbitPhase)
                val pz = cz + radius * sin(orbitPhase)

                val delta = org.joml.Vector3f(px, cy, pz).sub(status.pos)
                if (delta.length() > currentSpeed && currentSpeed > 0f) delta.normalize().mul(currentSpeed)
                status.velocity.set(delta)
                previousTravel = delta.length()

            }
        }
    }
}
