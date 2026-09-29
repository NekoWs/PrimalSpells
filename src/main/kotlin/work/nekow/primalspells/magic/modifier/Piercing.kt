package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 穿刺修正 —— 让投射物可以无限穿透（`maxHitTargets = -1`），代价是削减 8 点伤害。
 *
 * **伤害是加减法，与装配顺序无关**：所有修饰器都对同一份投射物状态做可交换的累加/累乘，
 * 不存在「谁先应用」的概念。例如「火球 + 伤害强化 + 穿刺」的伤害 =
 * 基础伤害 + 强化 − 8，结果为负时按**无伤害**结算（`Hurt` 只在 `damage > 0` 时结算）——
 * 「穿刺后打不出伤害」是这套定价的预期结果，不是 bug。
 *
 * 穿刺真正的收益是穿透 + 每 tick 持续命中（`Move` 每 tick 反复命中同一实体，
 * 配合 `Hurt` 清零无敌帧持续结算）。200 点法力的定价尚未调平。
 */
class Piercing : Modifier() {
    override val id = "piercing"

    init {
        mana = 200.0
        effects += object : BaseEffect() {
            override fun onActive() {
                projectile.maxHitTargets = -1
                status.damage -= 8.0
            }
        }
    }
}