package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 穿刺修正 —— 让投射物可以无限穿透（`maxHitTargets = -1`）。
 *
 * **代价是节奏，不是伤害**（第 26 轮重定价，见 [docs/design/combat-pricing.md] §2）：
 * 原本的「−8 伤害」在旧的 1 点伤害尺度下就把火球压成负数了，新尺度（火球 5）更不行，
 * 于是改成给法杖加 `delay 4 / recharge 8` —— 无限穿透的代价是「打完这一发要等」。
 * 200 点法力留在「法杖分等级」那一轮重调。
 *
 * 穿透真正的收益是穿多个目标 + 每 tick 持续命中（`Move` 每 tick 反复命中同一实体，
 * 配合 `Hurt` 清零无敌帧持续结算）。
 *
 * 另外：穿透弹**完全无视能量护盾**（`EnergyShield` 直接跳过 `maxHitTargets < 0` 的投射物）。
 */
class Piercing : Modifier() {
    override val id = "piercing"

    init {
        mana = 200.0
        delay = 0
        recharge = 0
        effects += object : BaseEffect() {
            override fun onActive() {
                projectile.maxHitTargets = -1
                projectile.selfDamage = true
            }
        }
    }
}
