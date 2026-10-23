package work.nekow.primalspells.magic.core

import org.joml.Vector3d
import org.joml.Vector3f

/**
 * 投射物的运行期状态袋：所有效果共享同一份实例，读写顺序由 [EffectPhase] 决定。
 *
 * 注意这里只放**当前帧需要**的数据：命中计数（[hitTargets]）、弹跳次数（[bounces]）、
 * 爆炸参数等。投射物位置/速度由 `Move` 在 [EffectPhase.MOTION] 阶段唯一推进。
 */
data class MagicStatus(
    /** 世界坐标必须保留double，远坐标也能逐1/16格积分。 */
    var pos: Vector3d = Vector3d(),
    var velocity: Vector3f = Vector3f(),
    var damage: Double = 0.0,
    /** 直接伤害被接受后附加的冻结时长；0 为关闭。 */
    var freezeTicks: Int = 0,
    var age: Int = 0,
    var bounces: Int = 0,
    var hitTargets: Int = 0,

    /**
     * 爆炸中心最大伤害（距离衰减, 与 TNT 爆炸伤害相当）。
     *
     * 这是**没显式定价的爆炸**的兜底值（`explosion_projectile` / `bounce_duplicate`
     * 的碎片都吃它）—— 只有真正挂了 `Explosion` 效果的法术才用得上这个字段，
     * 所以「护盾计强度」时必须先确认投射物带 `Explosion`，不能盲加（见 docs/design/combat-pricing.md §3）。
     */
    var explosionDamage: Double = 6.0,

    /** 爆炸半径（自定义爆炸结算用，不会破坏方块） */
    var explosionRadius: Float = 2.0f,
)
