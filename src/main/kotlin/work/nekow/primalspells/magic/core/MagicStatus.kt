package work.nekow.primalspells.magic.core

import org.joml.Vector3f

/**
 * 投射物的运行期状态袋：所有效果共享同一份实例，读写顺序由 [EffectPhase] 决定。
 *
 * 注意这里只放**当前帧需要**的数据：命中计数（[hitTargets]）、弹跳次数（[bounces]）、
 * 爆炸参数等。投射物位置/速度由 `Move` 在 [EffectPhase.MOTION] 阶段唯一推进。
 */
data class MagicStatus(
    var pos: Vector3f = Vector3f(),
    var velocity: Vector3f = Vector3f(),
    var damage: Double = 0.0,
    var age: Int = 0,
    var bounces: Int = 0,
    var hitTargets: Int = 0,

    /** 爆炸中心最大伤害（距离衰减, 默认与 TNT 爆炸伤害相当） */
    var explosionDamage: Double = 5.0,

    /** 爆炸半径（自定义爆炸结算用，不会破坏方块） */
    var explosionRadius: Float = 2.0f,
)
