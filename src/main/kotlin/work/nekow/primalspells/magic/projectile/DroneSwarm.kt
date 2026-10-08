package work.nekow.primalspells.magic.projectile

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntitySpawnReason
import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.registry.ModEntities
import work.nekow.primalspells.magic.effect.visual.Trajectory
import work.nekow.primalspells.util.Lore
import kotlin.math.cos
import kotlin.math.sin

/**
 * 无人机群投射物 —— 静态投射物，施放后在**法术释放位置**（投射物出生点）周围
 * 召唤 5 个无人机实体（DroneEntity）。存活 1 tick 后死亡，仅用于触发召唤效果。
 */
class DroneSwarm : Projectile() {

    override val id = "drone_swarm"
    override val bodyColor = Color.CYAN

    init {
        mana = 8.0
        delay = 0
        recharge = 0
        maxAge = 1
        speed = 0f; collisionEnabled = false
        hitRadius = 0.0
        effects += Trajectory(Color.CYAN)
    }

    /**
     * 施放后立即在法术释放位置周围生成 5 个无人机实体（DroneEntity）。
     */
    override fun onSpell() {
        val level = caster.level() as? ServerLevel ?: return
        // 法术释放位置：投射物出生点（status.pos 已在 effects.onActive 中设置）
        val center = status.pos

        // 在释放位置周围半径 2 格的圆上均匀分布 5 个实体
        val count = 5
        val radius = 2.0 * areaScale
        for (i in 0 until count) {
            // 计算等角分布的偏移
            val angle = 2.0 * Math.PI * i / count
            val offsetX = radius * cos(angle)
            val offsetZ = radius * sin(angle)

            // 生成目标位置（与释放位置同 Y 高度）
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
                entity.spellRecipe = work.nekow.primalspells.magic.core.SpellAssembly.inheritableOrder(this)
                entity.spellModifiers = work.nekow.primalspells.magic.core.SpellAssembly.inheritable(this)
                entity.owner = caster.uuid // 记录施法者，攻击时不误伤
            }
        }

        val manager = work.nekow.particledrawing.api.ParticleManager.of(level)
        val p = net.minecraft.world.phys.Vec3(center.x.toDouble(), center.y.toDouble(), center.z.toDouble())
        work.nekow.primalspells.magic.effect.visual.PixelVfx.ring(manager, p, net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0), radius * 0.4, Color.CYAN, 12, 3f)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(manager, p, Color.CYAN, 128, 0.15, 16)

    }

    /** 初始化物品描述 */
    override fun initLore() {
        super.initLore()
        lore(Lore.DESCRIPTION) // 添加自定义描述行
    }
}
