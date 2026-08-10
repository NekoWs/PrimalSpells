package work.nekow.primalspells.magic.effect

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * 追踪效果 —— 在延迟结束后每 tick 寻找球形范围内最近的实体,
 * 并将投射物的速度向该实体方向偏转（加速）。
 *
 * 叠加机制：多个 Tracking 效果叠加时，首个实例以 √N 倍率增强效果；
 * 其余实例不重复执行追踪逻辑。
 *
 * @param range            追踪范围（球形半径, 单位：方块）
 * @param strength         追踪强度（每 tick 向目标方向的加速度）
 * @param delayTicks       投射物射出后延迟多少 tick 才开始生效（默认 20 tick = 1 秒）
 * @param excludeItems     是否排除掉落物实体（默认 true）
 * @param excludeMinecarts 是否排除矿车实体（默认 true）
 * @param excludeBoats     是否排除船实体（默认 true）
 */
class Tracking(
    val range: Float,
    val strength: Float,
    val delayTicks: Int = 20,
    val excludeItems: Boolean = true,
    val excludeMinecarts: Boolean = true,
    val excludeBoats: Boolean = true
) : BaseEffect() {

    /** 上一 tick 锁定的目标实体, 用于检测目标切换以播放锁定音效 */
    private var lastTarget: Entity? = null

    /**
     * 每 tick 执行一次追踪逻辑
     *
     * 流程：
     * 1. 检查投射物存活状态, 已死亡则直接返回
     * 2. 检查延迟计数 —— 若 [MagicStatus.age] < [delayTicks], 暂不执行追踪
     * 3. 获取服务端世界实例（客户端不执行追踪逻辑）
     * 4. 统计投射物上所有 Tracking 实例数量, 仅首个实例执行追踪（其余跳过）
     * 5. 以 √N 倍率计算调整后的追踪范围和强度
     * 6. 构建球形搜索 AABB, 遍历范围内存活实体,
     *    按配置排除掉落物/矿车/船, 找出最近者
     * 7. 若找到目标：
     *    - 计算方向向量（单位向量）指向目标
     *    - 以调整后的追踪强度叠加到投射物速度
     *    - 若目标切换（首次锁定或更换目标）, 播放锁定音效
     * 8. 若无目标：重置锁定状态
     */
    override fun onTick() {
        // 投射物已死亡, 不执行追踪
        if (!projectile.alive) return

        // 未到达生效延迟, 暂时跳过追踪逻辑
        if (status.age < delayTicks) return

        // 获取服务端世界实例, 客户端直接跳过
        val level = caster.level() as? ServerLevel ?: return

        // 统计投射物上所有 Tracking 效果实例的数量
        // 仅列表中的首个 Tracking 实例实际执行追踪, 避免重复计算
        val trackingEffects = projectile.effects.filterIsInstance<Tracking>()
        if (trackingEffects.isEmpty() || trackingEffects.first() !== this) return

        // √N 叠加倍率：2 个 Tracking → √2 ≈ 1.41 倍, 3 个 → √3 ≈ 1.73 倍
        val count = trackingEffects.size
        val multiplier = Math.sqrt(count.toDouble()).toFloat()

        // 根据叠加倍率调整追踪范围和强度
        val adjustedRange = range * multiplier
        val adjustedStrength = strength * multiplier

        // 以投射物当前位置为中心, 构建球形搜索包围盒
        val center = Vec3(
            status.pos.x.toDouble(),
            status.pos.y.toDouble(),
            status.pos.z.toDouble()
        )
        val searchBox = AABB.ofSize(
            center,
            adjustedRange.toDouble() * 2.0,
            adjustedRange.toDouble() * 2.0,
            adjustedRange.toDouble() * 2.0
        )

        // 在搜索范围内查找最近的存活实体
        // - 排除施法者自身
        // - 按配置排除掉落物 / 矿车 / 船等非生物实体
        var nearestDistSq = Double.MAX_VALUE
        var nearestEntity: Entity? = null

        level.getEntities(null, searchBox) { entity ->
            entity != caster
                && entity.isAlive
                && !(excludeItems && entity is ItemEntity)
                && !(excludeMinecarts && entity is AbstractMinecart)
                && !(excludeBoats && entity is AbstractBoat)
        }.forEach { entity ->
            val dx = entity.x - status.pos.x
            val dy = entity.y - status.pos.y
            val dz = entity.z - status.pos.z
            val distSq = (dx * dx + dy * dy + dz * dz).toDouble()
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearestEntity = entity
            }
        }

        // 找到目标实体：执行追踪偏转
        nearestEntity?.let { target ->
            // 检查目标是否切换（首次锁定或追踪新目标时播放锁敌音效）
            if (lastTarget == null || lastTarget !== target) {
                // 播放 Sculk 传感器点击音效, 类似锁定提示音
                level.playSound(
                    null,                             // 播放给所有附近的玩家
                    status.pos.x.toDouble(),
                    status.pos.y.toDouble(),
                    status.pos.z.toDouble(),
                    SoundEvents.SCULK_CLICKING,       // Sculk 传感器点击声 —— 锁敌提示音
                    SoundSource.NEUTRAL,              // 环境音源
                    0.3f,                             // 较低音量
                    1.6f                              // 略高音调（清脆）
                )
                lastTarget = target
            }

            // 计算从投射物指向目标实体的方向向量（单位向量）
            val direction = Vector3f(
                (target.x - status.pos.x).toFloat(),
                (target.y - status.pos.y).toFloat(),
                (target.z - status.pos.z).toFloat()
            ).normalize()

            // 以调整后的追踪强度叠加方向向量到投射物当前速度上
            status.velocity.add(direction.mul(adjustedStrength))
        } ?: run {
            // 无实体在追踪范围内：重置锁定状态
            lastTarget = null
        }
    }
}
