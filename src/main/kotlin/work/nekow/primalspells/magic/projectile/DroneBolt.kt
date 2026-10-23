package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.Trajectory

/**
 * 无人机弹 —— `DroneEntity` 的专用弱弹（第 26 轮从 `fireball` 拆出来）。
 *
 * **为什么单独一个法术**：无人机的攻击**不经过牌堆与法力** ——
 * `DroneEntity.performRangedAttack` 自己 `SpellRegistry.create(...)` + `spell()` + `add`，
 * 所以它会**白拿那个法术的全部数值**。火球在 [docs/design/combat-pricing.md] §2 里被抬到
 * 5 伤害 / 10 tick 之后，5 架无人机就是 25 伤害/秒，反而变成全场最强单体输出。
 * 于是给无人机自己的低伤害定义（2 伤害），让它回到「骚扰 / 持续压制」的定位。
 *
 * 本类里的 `mana`/`delay`/`recharge` **只对「玩家真把这张卡装进法杖」有意义**
 * —— 无人机那条路径完全不看它们（既不看 `mana` 也不看 `delay`/`recharge`）。
 */
class DroneBolt : Projectile() {
    override val id = "drone_bolt"

    /** 冷绿：与火球的橙色区分开，一眼能看出「这是无人机的弹」 */
    override val bodyColor = Color.ofInt(120, 255, 200, 255)

    init {
        // 玩家侧定价：2 伤害 / 8 tick 间隔 ≈ 5 DPS（比火球低一档的弱法术）
        mana = 2.0
        delay = 0
        recharge = 0
        maxAge = 200
        status.damage = 2.0
        hitRadius = 0.2
        speed = 0.5f
        effects += Trajectory(bodyColor)
        effects += Hurt()
    }
}
