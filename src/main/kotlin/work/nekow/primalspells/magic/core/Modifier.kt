package work.nekow.primalspells.magic.core

import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 修饰器基类：描述「对投射物做哪些修正」，自身不进 [MagicManager.projectiles]、不参与 tick。
 *
 * 装配流程：`Wand.castHand` 把修饰器手牌里的效果克隆后**追加**进投射物的 `effects`
 * （见 [Modifier.effects]），随后随投射物一起 `spell()` / `tick()`。
 *
 * ## 硬性约定：修饰器之间**没有先后顺序**
 *
 * 手里抽到的所有修饰器都作用在**同一枚投射物**上，例如「火球 + 伤害强化 + 穿刺」的伤害 =
 * 基础伤害 + 强化 − 穿刺惩罚，不存在「谁先应用」的语义。因此：
 *
 * - `onActive` / `onTick` 只允许做**可交换**的运算：累加（`damage += x`）、累乘（`velocity *= k`）。
 * - 禁止「先读后写整值」的写法，例如 `status.damage = (status.damage - 8).coerceAtLeast(0.0)`
 *   或 `velocity.normalize().mul(绝对目标速度)` —— 这类写法会让结果依赖执行顺序。
 * - 需要「归零」时用乘法吸收律（`x *= 0`），它在任何顺序下结果一致。
 * - 需要按「当前值的增量」推进的效果（逐渐加速/减速），应用**倍率增量**（本 tick 倍率 ÷ 上 tick 倍率），
 *   而不是重新设置绝对值。
 * - 需要叠加强度的同类修饰器：用 [BaseEffect.stackScale] / [BaseEffect.isStackPrimary]（首个实例生效 + √N）。
 *
 * 同阶段内 `onActive`/`onTick` 按注入顺序执行（`sortedBy` 稳定排序），但这只是**确定性**要求，
 * 不应被任何效果依赖。
 */
abstract class Modifier : Magic() {

    /** 返回注入用的效果**克隆**：多枚子弹/多次释放各拿一份，避免共享可变状态。 */
    fun effects(): List<BaseEffect> {
        return effects.map { it.clone() }
    }
}
