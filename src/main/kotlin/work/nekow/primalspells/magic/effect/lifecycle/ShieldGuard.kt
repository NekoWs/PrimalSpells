package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.wand.Wand

/**
 * 「命中/爆炸 → 能量护盾」的**接入缝**。
 *
 * `Explosion` 与 `Hurt` 都是通用效果，不该认识某个具体法术；而能量护盾需要在**伤害结算之前**插一杠子
 * （抵扣成功要连击退一起免掉，事后补救做不到）。于是把这段查询单独放这里：
 * 调用方只问「这个实体手上的护盾能不能替我吃掉这一下」，具体规则（壳内/壳外、强度、破口、MP）
 * 全在 `EnergyShield` 里。
 *
 * 查的是**手上的法杖**（主手/副手 —— 与护盾 `needInventory` 的生效条件一致），
 * 不用任何全局注册表，因此没有注册/注销的生命周期问题。
 *
 * 各入口共用护盾规则：
 * - [interceptSegment]：Move的真实相邻子步，早于本体命中和光束绘制；
 * - [absorbExplosion]：独立爆炸点（`Explosion.customExplode`）；
 * - [absorbProjectile]：其它直接命中路径的伤害前兜底；
 * - [absorbVanillaProjectile]：原版投射物命中事件。
 *
 * 口径全文见 [docs/design/combat-pricing.md] §3。
 */
object ShieldGuard {

    /** 检查手顺序：主手优先（两把杖都带盾时，主手那把先有机会结算）。 */
    private val HANDS = arrayOf(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND)

    /**
     * 尝试替 [entity] 抵扣这次爆炸。
     *
     * @param falloffDamage 这颗爆炸**在 [entity] 处**的衰减后伤害（`Explosion` 已按距离算好）
     * @return true = 已抵扣，调用方应跳过该实体的伤害与击退
     */
    fun absorbExplosion(
        level: ServerLevel,
        entity: LivingEntity,
        center: Vec3,
        radius: Float,
        falloffDamage: Double,
    ): Boolean = withShield(entity) { shield, wand ->
        shield.absorbExplosion(level, wand, entity, center, radius, falloffDamage)
    }

    /** Move宽相位只保留真正持盾的实体，避免对子步中的每个生物反复查杖。 */
    fun hasShield(entity: LivingEntity): Boolean = HANDS.any { hand ->
        WandItem.getWand(entity.getItemInHand(hand))?.loadedMagics?.any { it is EnergyShield } == true
    }

    /** 持续光束按球壳实际交点排序，避免先扣远处护盾的法力。 */
    fun segmentEntry(entity: LivingEntity, from: Vector3d, to: Vector3d): Vec3? {
        for (hand in HANDS) {
            val wand = WandItem.getWand(entity.getItemInHand(hand)) ?: continue
            val shield = wand.loadedMagics.firstOrNull { it is EnergyShield } as? EnergyShield ?: continue
            shield.segmentEntry(entity, from, to)?.let { return it }
        }
        return null
    }

    /** 按本刻真实相邻子步测试球壳，早于本体命中和视觉阶段。 */
    fun interceptSegment(level: ServerLevel, entity: LivingEntity, projectile: Projectile,
                         from: Vector3d, to: Vector3d): Boolean =
        !(projectile.damageType == SpellDamageType.HEALING && projectile.status.damage > 0) && withShield(entity) { shield, wand ->
            shield.absorbProjectile(level, wand, entity, projectile, from, to)
        }.also { if (it) projectile.shieldedImpactTargets += entity.uuid }

    /**
     * 尝试让 [entity] 手上的护盾**格挡这枚即将命中它的投射物**。
     *
     * @return true = 已格挡（调用方应跳过伤害），投射物已被护盾反射
     */
    fun absorbProjectile(level: ServerLevel, entity: LivingEntity, projectile: Projectile): Boolean =
        !(projectile.damageType == SpellDamageType.HEALING && projectile.status.damage > 0) && withShield(entity) { shield, wand ->
            shield.absorbProjectile(level, wand, entity, projectile)
        }.also { if (it) projectile.shieldedImpactTargets += entity.uuid }

    /**
     * 尝试让 [entity] 手上的护盾**防御/反弹一枚原版投射物**（第 40 轮；入口是 `ProjectileImpactEvent`）。
     *
     * 与 [absorbProjectile] 同一套路：原版箭矢 3 格/tick，可能在同一 tick 内就贴到身上，
     * 所以除了每 tick 的扫场，命中那一刻还要再问一次护盾。
     *
     * @return true = 已格挡（调用方应**取消这次命中**；投射物已被反弹）
     */
    fun absorbVanillaProjectile(
        level: ServerLevel,
        entity: LivingEntity,
        projectile: net.minecraft.world.entity.projectile.Projectile,
    ): Boolean = withShield(entity) { shield, wand ->
        shield.absorbVanillaProjectile(level, wand, entity, projectile)
    }

    /** 按「主手 → 副手」找出带护盾的法杖，交给护盾自己判断；第一把说不挡就试下一把。 */
    private inline fun withShield(entity: LivingEntity, action: (EnergyShield, Wand) -> Boolean): Boolean {
        for (hand in HANDS) {
            val wand = WandItem.getWand(entity.getItemInHand(hand)) ?: continue
            val shield = wand.loadedMagics.firstOrNull { it is EnergyShield } as? EnergyShield ?: continue
            if (action(shield, wand)) return true
        }
        return false
    }
}
