package work.nekow.primalspells.magic.modifier

import org.joml.Vector3f
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.random.Random

/**
 * 随机路径修正 —— 每 tick 给投射物方向一个随机偏转（随机游走），速度大小保持不变。
 *
 * **可叠加（顺序无关）**：只把**当前**方向随机旋转一个小角度，而不是像旧实现那样
 * 每 tick 重新生成一个完全随机的方向 —— 后者会立刻抹掉 `reciprocate`（往复反向）、
 * `homing`（追踪）等修饰器对方向的改动。
 *
 * 叠加规则：不做特殊处理（每个实例各自施加自己的随机扰动）。
 */
class RandomPath : Modifier() {

    override val id = "random_path"

    init {
        mana = 3.0
        delay = 1

        effects += object : BaseEffect() {

            /** 每 tick 对当前方向施加一次随机偏转 */
            override fun onTick() {
                if (!projectile.alive) return

                val speed = status.velocity.length()
                if (speed <= 0.001f) return

                // 随机小偏转：方向随机、幅度在 [0, MAX_TURN_PER_TICK] 之间
                val jitter = Vector3f(
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f
                ).normalize().mul(Random.nextFloat() * MAX_TURN_PER_TICK)

                val dir = Vector3f(status.velocity).div(speed).add(jitter).normalize()
                status.velocity.set(dir).mul(speed)
            }
        }
    }

    private companion object {
        /** 单 tick 最大偏转量（约 20°），数值越大路径越抖 */
        const val MAX_TURN_PER_TICK = 0.35f
    }
}
