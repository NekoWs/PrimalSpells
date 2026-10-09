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
 * 同一法术链里的所有修饰器统一作用在**本链所有投射物**上，触发荷载是独立链，例如「火球 + 伤害强化 + 穿刺」的伤害 =
 * 基础伤害 + 强化 − 穿刺惩罚，不存在「谁先应用」的语义。因此：
 *
 * - `onActive` / `onTick` 优先做同类可交换运算：累加（`damage += x`）、累乘（`velocity *= k`）。
 *   加法与乘法混用并不可交换，当前由SpellAssembly的规范化配方保证加值先于倍率，不能用牌序决定。
 * - 禁止「先读后写整值」的写法，例如 `status.damage = (status.damage - 8).coerceAtLeast(0.0)`
 *   或 `velocity.normalize().mul(绝对目标速度)` —— 这类写法会让结果依赖执行顺序。
 * - 需要「归零」时用乘法吸收律（`x *= 0`）；必须在对应加值之后结算，避免零值被重新加回。
 * - 需要按「当前值的增量」推进的效果（逐渐加速/减速），应用**倍率增量**（本 tick 倍率 ÷ 上 tick 倍率），
 *   而不是重新设置绝对值。
 * - 需要叠加强度的同类修饰器：用 [BaseEffect.stackScale] / [BaseEffect.isStackPrimary]（首个实例生效 + √N）。
 *
 * SpellAssembly.apply将本链修正按id规范化装配，加法伤害在倍率前结算。
 * 同阶段内按规范化后的注入顺序执行，禁止让玩家的卡牌排列改变效果；独立荷载不继承父链修正。
 */
abstract class Modifier : Magic() {

    /** 返回注入用的效果**克隆**：多枚子弹/多次释放各拿一份，避免共享可变状态。 */
    fun effects(): List<BaseEffect> {
        return effects.map { it.clone() }
    }
}
