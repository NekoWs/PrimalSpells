package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 周身环绕修正 —— 投射物射出后在施法者周围水平面（XZ）上做圆周运动。
 *
 * 轨道半径 = min(当前速度 × 1.0, 5.0)，下限 1.0；环绕角速度 = 当前速度 × 0.16，范围 [0.04, 0.4]。
 *
 * 注入的效果声明为 [EffectPhase.OVERRIDE]：它在 `Move` 完成位置积分**之后**设置绝对轨道位置，
 * 因此不会被积分位移叠加（这是过去轨道会漂移的原因）。同时把 `status.velocity` 写成轨道切向速度，
 * 供拖尾方向与下一 tick 的半径/角速度计算使用。
 */
class SelfOrbit : Modifier() {

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
        delay = 0

        effects += object : BaseEffect() {
            override val phase = EffectPhase.OVERRIDE

            /** 轨道相位（弧度），施法瞬间按出生方位初始化 */
            private var orbitPhase = 0f

            override fun onActive() {
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

                val currentSpeed = status.velocity.length()

                // 轨道半径随当前速度动态更新
                val radius = (currentSpeed * radiusFactor).coerceIn(1.0f, radiusCap)

                // 角速度随当前速度动态更新
                val angularSpeed = (currentSpeed * angularSpeedFactor).coerceIn(minAngularSpeed, maxAngularSpeed)

                orbitPhase += angularSpeed

                val px = cx + radius * cos(orbitPhase)
                val pz = cz + radius * sin(orbitPhase)

                status.pos.set(px, cy, pz)
                status.velocity.set(
                    -radius * angularSpeed * sin(orbitPhase),
                    0f,
                    radius * angularSpeed * cos(orbitPhase)
                )
            }
        }
    }
}
