package work.nekow.primalspells.magic.effect

import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.MagicStatus
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.wand.Wand
import kotlin.math.sqrt

/**
 * 效果基类：生命周期钩子 + 执行阶段 + 同类叠加辅助。
 *
 * 生命周期（由 [work.nekow.primalspells.magic.core.Magic] 统一转发）：
 * - `onActive` 施法瞬间一次；`onTick` 每 tick；`onHitEntity`/`onHitBlock`/`onHit` 命中时；
 * - `onExpire` 寿命耗尽当刻；`onDie` 死亡后的清理。
 *
 * 执行顺序由 [phase] 决定，不再依赖 `effects` 的插入顺序 —— 详见 [EffectPhase]。
 * `caster`/`wand`/`status`/`projectile` 由 [work.nekow.primalspells.magic.core.Magic.spell] 与
 * [Projectile.spell] 在钩子调用前注入。
 */
open class BaseEffect : Cloneable {
    lateinit var caster: Entity
    lateinit var wand: Wand
    lateinit var status: MagicStatus
    lateinit var projectile: Projectile

    /**
     * 本效果的执行阶段，默认 [EffectPhase.MODIFIER]（玩法效果，在位置积分前改输入量）。
     * 需要绝对定位的效果请声明 [EffectPhase.OVERRIDE]，需要读最终位置发粒子的请声明 [EffectPhase.VISUAL]。
     */
    open val phase: EffectPhase = EffectPhase.MODIFIER

    /** 投射物上**同类型**效果的数量 N（多个相同修饰器叠加时用）。 */
    val stackCount: Int
        get() = projectile.effects.count { it::class == this::class }

    /** 同类叠加的强度系数 √N。 */
    val stackScale: Float
        get() = sqrt(stackCount.toFloat())

    /**
     * 自己是否为同类效果中的**首个**实例。
     *
     * 约定：多个同类效果叠加时只有首个实例改写状态，其余实例仅用于把 [stackScale] 抬高，
     * 否则 N 个实例会把同一份逻辑结算 N 次（这也是「首个实例 + √N」样板的由来）。
     */
    val isStackPrimary: Boolean
        get() = projectile.effects.firstOrNull { it::class == this::class } === this

    open fun onTick() { }
    open fun onActive() { }
    open fun onHitEntity(target: Entity) { }
    open fun onHitBlock(pos: Vector3d) { }
    open fun onHit(result: HitResult) { }
    open fun onExpire() { }

    open fun onDie() { }

    public override fun clone(): BaseEffect = super.clone() as BaseEffect
}
