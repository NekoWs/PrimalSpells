package work.nekow.primalspells.magic.effect.geometry

import org.joml.Vector3d
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 出生点：`onActive` 时把 [MagicStatus.pos] 设为投射物的起始位置。
 *
 * 阶段：[EffectPhase.SETUP] —— 必须最先执行，后续所有效果都建立在它写好的位置上。
 */
class Position(
    var pos: Vector3d
): BaseEffect() {
    override val phase = EffectPhase.SETUP

    override fun onActive() {
        status.pos = Vector3d(pos)
    }
}