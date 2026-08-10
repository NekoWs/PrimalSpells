package work.nekow.primalspells.magic.revise

import org.joml.Vector3f
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.random.Random

/**
 * 随机路径修正 —— 每 tick 将投射物方向完全随机化,
 * 速度大小保持不变。
 *
 * 多个修正叠加：不做特殊处理（各自独立运行）。
 */
class RandomPath : Revise() {

    override val id = "random_path"

    init {
        mana = 3.0
        delay = 1

        effects += object : BaseEffect() {

            /**
             * 每 tick 生成随机单位方向，保持原速度大小
             */
            override fun onTick() {
                if (!projectile.alive) return

                val speed = status.velocity.length()
                if (speed <= 0.001f) return

                // 完全随机方向
                val dir = Vector3f(
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f
                ).normalize()

                status.velocity.set(dir).mul(speed)
            }
        }
    }
}
