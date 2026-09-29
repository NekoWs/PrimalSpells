package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.geometry.ConstantAcceleration

/**
 * 重力修正 —— 让投射物像抛体一样持续下坠。
 *
 * 注入共用的 [ConstantAcceleration]（每 tick 速度 y 减 0.03），与 `ClusterBomb` 生成的子弹
 * 使用同一实现，避免同一条逻辑在多处各写一份。
 */
class Gravity : Modifier() {
    override val id = "gravity"

    init {
        effects += ConstantAcceleration()
    }
}
