package work.nekow.primalspells.magic.modifier

import org.joml.Vector3f
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore
import kotlin.math.sin

/**
 * 蛇形修正（法术 id 仍为 `sine_wave`）—— 让投射物沿前进方向做正弦起伏，轨迹像蛇一样扭动。
 *
 * 振荡方式：以施法瞬间的速度方向为波形中心线，每 tick 按 `sin(频率 · 年龄) × 振幅` 取一个
 * 竖直偏移，得到本 tick 的「波形方向」。
 *
 * **可叠加（顺序无关）**：本效果每 tick 只把「波形方向相对上一 tick 的变化量」作为**旋转增量**
 * 叠加到**当前**速度方向上，而不是把方向整个覆写成波形方向。
 * 因此 `reciprocate`（往复运动的反向）、`homing`（追踪偏转）、`random_path`（随机扰动）
 * 对方向的改动都能保留 —— 旧实现每 tick 覆写方向，会直接无视往复运动。
 *
 * 多个实例叠加：仅首个实例生效，频率与振幅 × √N。
 */
class Serpentine : Modifier() {

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

            /** 施法瞬间的基准前进方向（固定不变，作为波形中心线） */
            private var baseForward: Vector3f? = null

            /** 上一 tick 的波形方向，用于取旋转增量 */
            private var lastWaveDir: Vector3f? = null

            /** 记录施法瞬间的速度方向作为波形中心线（仅首个实例） */
            override fun onActive() {
                if (!isStackPrimary) return
                if (status.velocity.lengthSquared() < 0.0001f) return
                baseForward = Vector3f(status.velocity).normalize()
            }

            /** 每 tick 叠加一次波形旋转增量，保持速度大小不变 */
            override fun onTick() {
                if (!projectile.alive) return
                if (!isStackPrimary) return

                val forward = baseForward ?: return
                val speed = status.velocity.length()
                if (speed <= 0.001f) return

                // √N 叠加倍率（见 BaseEffect.stackScale）
                val adjustedFreq = baseFreq * stackScale
                val adjustedAmp = baseAmp * stackScale
                // adjustedFreq 与 status.age 相乘仍是 Float，sin(Float) 返回 Float，无需再 toFloat()
                val yOffset = adjustedAmp * sin(adjustedFreq * status.age)

                val waveDir = Vector3f(forward).add(Vector3f(worldUp).mul(yOffset)).normalize()
                val prevWaveDir = lastWaveDir
                lastWaveDir = Vector3f(waveDir)
                if (prevWaveDir == null) return // 第一 tick 只建立基线，不改方向

                val delta = Vector3f(waveDir).sub(prevWaveDir)
                val dir = Vector3f(status.velocity).div(speed).add(delta).normalize()
                status.velocity.set(dir).mul(speed)
            }
        }

        lore(Lore.SPEED, "%.2f / %.2f".format(baseFreq, baseAmp))
    }
}
