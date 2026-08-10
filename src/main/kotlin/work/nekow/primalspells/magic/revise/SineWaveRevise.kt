package work.nekow.primalspells.magic.revise

import org.joml.Vector3f
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 正弦波修正 —— 使投射物在竖直方向（Y 轴）做正弦摆动。
 *
 * 振荡方式：
 * - 以施法瞬间的速度方向为前进方向
 * - 每 tick 将世界 Y 轴方向乘以 sin 偏移量叠加到前进方向上
 * - 与玩家朝向无关，始终沿 Y 轴上下震荡
 *
 * 多个修正叠加：频率和振幅 × √N。
 */
class SineWaveRevise : Revise() {

    override val id = "sine_wave"

    /** 基础正弦摆动频率 */
    private val baseFreq = 0.15f

    /** 基础正弦摆动振幅 */
    private val baseAmp = 0.4f

    /** 世界 Y 轴向上单位向量 */
    private val worldUp = Vector3f(0f, 1f, 0f)

    init {
        mana = 3.0
        delay = 1

        effects += object : BaseEffect() {

            /** 施法瞬间的基准前进方向（固定不变, 用于维持主运动方向） */
            private var baseForward: Vector3f? = null

            /**
             * 记录施法瞬间的速度方向作为基准前进方向
             */
            override fun onActive() {
                if (status.velocity.lengthSquared() < 0.0001f) return
                baseForward = Vector3f(status.velocity).normalize()
            }

            /**
             * 每 tick 在竖直方向叠加正弦偏移
             *
             * 新方向 = normalize(基准前进方向 + Y轴 · sin(频率 · 年龄) · 振幅)
             *
             * √N 叠加：仅首个同类型实例执行，频率和振幅 × √N
             */
            override fun onTick() {
                if (!projectile.alive) return
                val fwd = baseForward ?: return

                // 仅首个同类型实例执行
                val cls = this::class
                val instances = projectile.effects.filter { it::class == cls }
                if (instances.firstOrNull() !== this) return

                // √N 叠加倍率
                val factor = sqrt(instances.size.toDouble()).toFloat()
                val adjustedFreq = baseFreq * factor
                val adjustedAmp = baseAmp * factor

                val speed = status.velocity.length()
                if (speed <= 0.001f) return

                // 正弦偏移量：sin(freq × age) × amplitude
                val sinVal = sin(adjustedFreq * status.age).toFloat()
                val yOffset = adjustedAmp * sinVal

                // 新方向 = normalize(基准前进方向 + Y轴 · yOffset)
                val yShift = Vector3f(worldUp).mul(yOffset)
                val newDir = Vector3f(fwd).add(yShift).normalize()

                // 设置新方向，保持速度大小不变
                status.velocity.set(newDir).mul(speed)
            }
        }

        lore(Lore.SPEED, "%.2f / %.2f".format(baseFreq, baseAmp))
    }
}
