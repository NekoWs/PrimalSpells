package work.nekow.primalspells.magic.effect.geometry

import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 初速：`onActive` 时把 [MagicStatus.velocity] 设为投射物的初始速度。
 *
 * 阶段：[EffectPhase.SETUP] —— 必须在修饰器注入的效果（如速度提升/逐渐加速）之前执行，
 * 否则它们对速度的修改会被这里覆盖。
 */
class Velocity(
    val velocity: Vector3f
): BaseEffect() {
    override val phase = EffectPhase.SETUP

    override fun onActive() {
        status.velocity = velocity
    }
}