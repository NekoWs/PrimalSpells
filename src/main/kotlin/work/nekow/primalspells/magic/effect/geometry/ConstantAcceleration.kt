package work.nekow.primalspells.magic.effect.geometry

import org.joml.Vector3f
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 恒定加速度 —— 重力类效果共用的实现：每 tick 把 [acceleration] 累加到投射物速度上。
 *
 * 默认竖直向下（`-0.03` 格/tick²），也可传其它方向做侧向或反向加速。
 * 只改速度、不改位置，因此按默认的 [work.nekow.primalspells.magic.core.EffectPhase.MODIFIER]
 * 阶段在位置积分之前执行即可。
 */
class ConstantAcceleration(
    private val acceleration: Vector3f = Vector3f(0f, -0.03f, 0f),
) : BaseEffect() {
    override fun onTick() {
        status.velocity.add(acceleration)
    }
}
