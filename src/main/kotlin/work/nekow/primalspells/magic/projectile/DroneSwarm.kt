package work.nekow.primalspells.magic.projectile

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntitySpawnReason
import work.nekow.primalspells.entity.DroneEntity
import work.nekow.primalspells.entity.ModEntities
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.utils.Lore
import kotlin.math.cos
import kotlin.math.sin

/**
 * 无人机群投射物 —— 静态投射物，施放后在施法者周围召唤 5 个无人机实体（DroneEntity）。
 * 存活 1 tick 后死亡，仅用于触发召唤效果。
 */
class DroneSwarm : Projectile() {

    override val id = "drone_swarm"

    init {
        mana = 8.0 // 法力消耗
        delay = 1 // 施法延迟
        recharge = 2 // 充能时间
        maxAge = 1 // 存活 1 tick 后死亡
        speed = 0f // 静态投射物，不移动
        hitRadius = 0.0 // 无需碰撞检测
        // 添加粒子轨迹效果（使用电光火花粒子）
        effects += Trajectory(ParticleTypes.ELECTRIC_SPARK)
    }

    /**
     * 施放后立即在施法者周围生成 5 个无人机实体（DroneEntity）。
     */
    override fun onSpell() {
        val level = caster.level() as? ServerLevel ?: return
        val center = caster.position()

        // 在施法者周围半径 2 格的圆上均匀分布 5 个实体
        val count = 5
        val radius = 2.0
        for (i in 0 until count) {
            // 计算等角分布的偏移
            val angle = 2.0 * Math.PI * i / count
            val offsetX = radius * cos(angle)
            val offsetZ = radius * sin(angle)

            // 生成目标位置（与施法者同 Y 高度）
            val spawnX = center.x + offsetX
            val spawnY = center.y + 0.25 // 偏移修正：使碰撞箱底部与地面持平
            val spawnZ = center.z + offsetZ

            // 通过 EntityType.spawn 创建无人机实体并加入世界
            val entity = ModEntities.DRONE.get().spawn(
                level, // 服务端世界
                BlockPos.containing(spawnX, spawnY, spawnZ), // 生成所在方块
                EntitySpawnReason.MOB_SUMMONED // 生成原因
            )
            // 覆盖默认位置为精确坐标，并绑定施法者 UUID 以排除友军伤害
            if (entity != null) {
                entity.setPos(spawnX, spawnY, spawnZ)
                entity.owner = caster.uuid // 记录施法者，攻击时不误伤
            }
        }

        // 生成施法粒子效果
        for (i in 0..7) {
            val px = center.x + (level.random.nextDouble() - 0.5) * 2.0
            val py = center.y + level.random.nextDouble() * 2.0
            val pz = center.z + (level.random.nextDouble() - 0.5) * 2.0
            level.sendParticles(
                ParticleTypes.WARPED_SPORE,
                px, py, pz,
                1,
                0.0, 0.0, 0.0,
                0.5
            )
        }
    }

    /** 初始化物品描述 */
    override fun initLore() {
        super.initLore()
        lore(Lore.DESCRIPTION) // 添加自定义描述行
    }
}
