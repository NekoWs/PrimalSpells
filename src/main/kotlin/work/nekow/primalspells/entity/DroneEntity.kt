package work.nekow.primalspells.entity

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.monster.RangedAttackMob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.wand.Wand
import java.util.*
import kotlin.math.min

/**
 * 无人机动态实体 —— 拥有 AI，可漂浮、移动、攻击。
 *
 * 移动具有平滑加速曲线和随机路径偏移（0.2 倍噪音），降低机械感。
 * 无人机之间自动保持最小距离，减少碰撞箱性能消耗。
 */
class DroneEntity(
    entityType: EntityType<out DroneEntity>,
    level: Level
) : PathfinderMob(entityType, level), RangedAttackMob {

    /** 内存缓存的施法者 UUID，实际持久化在 persistentData 中 */
    private var cachedOwner: UUID? = null

    /**
     * 施法者 UUID 属性 —— 读取时优先从 persistentData 恢复，
     * 写入时同步更新内存缓存和 persistentData。
     */
    var owner: UUID?
        get() {
            if (cachedOwner == null) {
                val s = persistentData.getString("OwnerUUID").orElse("")
                if (s.isNotEmpty()) {
                    cachedOwner = UUID.fromString(s)
                }
            }
            return cachedOwner
        }
        set(value) {
            cachedOwner = value
            if (value != null) {
                persistentData.putString("OwnerUUID", value.toString())
            } else {
                persistentData.remove("OwnerUUID")
            }
        }

    /** 攻击冷却计时器 */
    var attackCooldown = 0

    /** 移动持续时间（tick），用于加速曲线 */
    private var moveAge = 0

    /** 速度曲线所需的 ramp 周期数 */
    private val rampTicks = 15

    /** 施放法术所需的虚拟法杖 */
    private val droneWand = Wand("drone_attack")

    init {
        this.setNoGravity(true)
        this.moveControl = FlyingMoveControl(this, 20, true)
        this.setPathfindingMalus(PathType.FIRE, -1f)
        this.setPathfindingMalus(PathType.WATER, -1f)
    }

    override fun registerGoals() {
        this.goalSelector.addGoal(0, FloatGoal(this))
        this.goalSelector.addGoal(0, DroneHeightGoal(this))
        this.goalSelector.addGoal(1, DroneAttackGoal(this, 1.0, 20, 10.0f))
        this.goalSelector.addGoal(2, DroneFollowOwnerGoal(this, 0.7))
        this.goalSelector.addGoal(3, DroneRandomStrollGoal(this, 0.5))
        this.goalSelector.addGoal(4, LookAtPlayerGoal(this, Player::class.java, 8.0f))
        this.goalSelector.addGoal(5, LookAtPlayerGoal(this, LivingEntity::class.java, 8.0f))
    }

    override fun createNavigation(level: Level): PathNavigation {
        val navigation = FlyingPathNavigation(this, level)
        navigation.setCanOpenDoors(false)
        navigation.setCanFloat(true)
        return navigation
    }

    override fun tick() {
        super.tick()

        if (attackCooldown > 0) attackCooldown--

        // 有寻路目标时累积移动计数，否则重置
        moveAge = if (navigation.path != null) min(moveAge + 1, rampTicks) else 0

        applySpeedCurve() // 平滑加速/减速
        applySeparation() // 无人机间距保持
        applyPathNoise() // 随机路径偏移
    }

    override fun performRangedAttack(target: LivingEntity, distanceFactor: Float) {
        if (attackCooldown > 0) return
        attackCooldown = 10

        val fireball = MagicManager.create("fireball") as? Projectile ?: return
        fireball.caster = this
        fireball.wand = droneWand
        val eyePos = this.eyePosition
        fireball.position = Vector3f(eyePos.x.toFloat(), eyePos.y.toFloat(), eyePos.z.toFloat())
        val targetCenter = target.position().add(0.0, target.bbHeight / 2.0, 0.0)
        val dir = targetCenter.subtract(eyePos).normalize()
        fireball.velocity = Vector3f(dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        fireball.spell()
        MagicManager.add(fireball)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        if (source.entity is DroneEntity) return false
        return super.hurtServer(level, source, amount)
    }

    // ──────────────── 移动优化 ────────────────

    /**
     * 速度曲线：开始移动时最慢（0.3x），中间逐渐加速至全速（1.0x），
     * 接近目标或停止时缓慢减速，使移动不再单一线性。
     */
    private fun applySpeedCurve() {
        val d = deltaMovement
        if (d.lengthSqr() < 1e-6) return // 未移动则不处理

        // 使用 ease-in-out 三次函数：f(t) = 3t² - 2t³，t ∈ [0, 1]
        val t = moveAge.toFloat() / rampTicks
        val factor = if (t <= 0f) 0.3f // 刚起步
        else if (t >= 1f) 1.0f // 全速
        else 0.3f + 0.7f * (3f * t * t - 2f * t * t * t) // 平滑过渡

        deltaMovement = d.scale(factor.toDouble())
    }

    /**
     * 无人机分离：在 1.5 格范围内互相排斥，
     * 保持随机最小距离（1.0～1.5 格），减少碰撞箱计算。
     */
    private fun applySeparation() {
        val nearby = level().getEntitiesOfClass(
            DroneEntity::class.java,
            boundingBox.inflate(1.5),
        ) { it !== this }
        if (nearby.isEmpty()) return

        var push = Vec3.ZERO
        for (other in nearby) {
            val diff = position().subtract(other.position())
            val dist = diff.length()
            if (dist < 1.5 && dist > 0.01) {
                // 距离越近推力越大
                val strength = 0.05 / (dist * dist)
                push = push.add(diff.normalize().scale(strength))
            }
        }
        // 限制总推力，避免瞬移
        if (push.lengthSqr() > 0.04) push = push.normalize().scale(0.2)
        addDeltaMovement(push)
    }

    /**
     * 随机路径偏移：给当前速度叠加 0.2 倍的随机方向噪音，
     * 模拟非精确寻路，运动更自然。
     */
    private fun applyPathNoise() {
        val d = deltaMovement
        if (d.lengthSqr() < 1e-6) return

        val r = random
        val noise = Vec3(
            (r.nextDouble() - 0.5) * 0.04, // X 噪音 ±0.02
            (r.nextDouble() - 0.5) * 0.04, // Y 噪音 ±0.02
            (r.nextDouble() - 0.5) * 0.04  // Z 噪音 ±0.02
        )
        // 仅当移动速度大于噪音阈值时叠加
        val speed = d.length()
        if (speed > 0.05) {
            val noiseScaled = noise.scale(0.2) // 0.2 倍噪音强度
            deltaMovement = d.add(noiseScaled)
        }
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder {
            return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.6)
                .add(Attributes.FLYING_SPEED, 2.0)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
        }
    }
}

/**
 * 无人机漫游目标 —— 在当前位置附近随机飞行。
 */
class DroneRandomStrollGoal(
    mob: PathfinderMob,
    speedModifier: Double
) : RandomStrollGoal(mob, speedModifier, 10) {
    override fun canUse(): Boolean {
        return mob.target == null && super.canUse()
    }
}
