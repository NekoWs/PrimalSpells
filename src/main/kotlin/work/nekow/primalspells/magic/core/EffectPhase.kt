package work.nekow.primalspells.magic.core

/**
 * 效果执行阶段 —— 把「谁先跑」从列表插入顺序变成显式契约。
 *
 * 背景：[Magic.effects] 里既有投射物内建效果（出生点/初速/位置积分/粒子），也有修饰器注入的玩法效果。
 * 过去两者只靠插入顺序隐式决定先后，出现了「在 `onTick` 里原地写 `status.pos` 的效果会被
 * [work.nekow.primalspells.magic.effect.geometry.Move] 的位移覆盖」这类难以察觉的漂移。
 * 现在每个效果声明自己的 [phase]，`Magic.spell()` / `Magic.tick()` 按阶段稳定排序执行
 * （同阶段内保持插入顺序）。
 *
 * 排序规则：
 * - `onActive` 按 [activeOrder] 升序：[SETUP] 先写入出生点与初速，[MODIFIER] 才能在此基础上改数值。
 * - `onTick` 按 [tickOrder] 升序：[MODIFIER] 改速度/伤害 → [GUIDANCE] 轨道导向 → [MOTION] 扫掠积分 → [OVERRIDE] 位置覆盖 → [VISUAL] 粒子。
 */
enum class EffectPhase(val activeOrder: Int, val tickOrder: Int) {
    /**
     * 出生与初速（`Position` / `Velocity`）：`onActive` 最先执行，负责把 [MagicStatus] 初始化成有效状态。
     * 其余阶段的效果都建立在它写好的 `pos`/`velocity` 之上。
     */
    SETUP(0, 0),

    /**
     * 玩法效果（修饰器注入的效果，默认阶段）：`onActive` 在 [SETUP] 之后；
     * `onTick` 在位置积分**之前**，改的是速度/伤害这类「输入量」，而不是最终位置。
     */
    MODIFIER(1, 1),

    /** 轨道导向：修正器改速后，Move扫掠前计算目标位移；出生在SETUP/修正之后。 */
    GUIDANCE(3, 2),

    /** 位置积分（`Move`）：扫掠轨迹、检测碰撞与分派命中。 */
    MOTION(2, 3),

    /**
     * 位置覆盖（命中后的连锁电弧等）：在 [MOTION] **之后**定位。
     * 需要轨迹碰撞的环绕效果使用 [GUIDANCE]，不在此阶段直接跳过扫掠。
     */
    OVERRIDE(3, 4),

    /**
     * 表现与粒子（`ProjectileBody`/`Trajectory`）：最后执行，读到的永远是本 tick 的最终位置与速度。
     */
    VISUAL(4, 5),
}
