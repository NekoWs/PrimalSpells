package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.Tracking
import work.nekow.primalspells.utils.Lore

/**
 * 追踪修正 —— 为投射物附加追踪能力
 *
 * 使用 [Tracking] 效果使投射物在飞行过程中自动偏向最近的实体目标。
 * 多个追踪修正叠加时，追踪范围和强度以 √N 倍率增长。
 *
 * 默认参数：
 * - 追踪范围：5.0 方块（球形半径）
 * - 追踪强度：0.1（每 tick 向目标方向的加速度）
 * - 生效延迟：20 tick（投射物射出 1 秒后开始追踪）
 * - 自动排除掉落物、矿车、船
 */
class Homing : Revise() {

    /** 修正的唯一标识符，与语言文件、模型、纹理文件名称对应 */
    override val id = "homing"

    /** 默认追踪范围（球形半径, 单位：方块） */
    private val trackingRange = 5.0f

    /** 默认追踪强度（每 tick 加速度） */
    private val trackingStrength = 0.1f

    /** 追踪生效前的延迟（单位：tick, 20 tick = 1 秒） */
    private val trackingDelay = 20

    init {
        /** 施放该修正消耗的法力值 */
        mana = 4.0

        /** 该修正增加的施法延迟（单位：tick） */
        delay = 1

        // 向修正效果列表中添加 Tracking 追踪效果
        // 传入追踪范围、强度、生效延迟，自动排除掉落物/矿车/船
        effects += Tracking(
            range = trackingRange,
            strength = trackingStrength,
            delayTicks = trackingDelay,
            excludeItems = true,
            excludeMinecarts = true,
            excludeBoats = true
        )

        // 添加到物品提示信息中
        lore(Lore.SPEED, "%.1f".format(trackingStrength))
    }
}
