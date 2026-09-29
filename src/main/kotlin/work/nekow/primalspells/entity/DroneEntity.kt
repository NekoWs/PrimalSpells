package work.nekow.primalspells.entity

import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
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
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.wand.Wand
import java.util.*
import kotlin.math.atan2
import kotlin.math.min

class DroneEntity(
    entityType: EntityType<out DroneEntity>,
    level: Level
) : PathfinderMob(entityType, level), RangedAttackMob {

    var owner: UUID?
        get() {
            if (_owner == null) {
                val s = persistentData.getString("OwnerUUID").orElse("")
                if (s.isNotEmpty()) _owner = UUID.fromString(s)
            }
            return _owner
        }
        set(value) {
            _owner = value
            if (value != null) persistentData.putString("OwnerUUID", value.toString())
            else persistentData.remove("OwnerUUID")
        }
    private var _owner: UUID? = null

    var attackCooldown = 0
    private var moveAge = 0
    private val rampTicks = 15
    private val droneWand = Wand("drone_attack")
    private var localTickCounter = 0

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
        localTickCounter++
        if (attackCooldown > 0) attackCooldown--

        if (localTickCounter % 3 == 0) {
            moveAge = if (navigation.path != null) min(moveAge + 3, rampTicks) else 0
        }
        // 以下四项都会直接改 deltaMovement / yRot，而 tick() 在客户端与服务端都会执行：
        // 客户端没有服务端 navigation.path（moveAge 恒为 0）、实体 random 序列也不同、
        // 且客户端 deltaMovement 每 tick 还会被 aiStep 衰减——两侧算出的轨迹必然分叉。
        // 客户端本地写入只会与「服务端同步 + 插值」的结果互相对抗，表现为朝向乱晃/位置抖动。
        // 因此一切运动扰动只由服务端权威计算一次，客户端只负责插值服务端同步过来的位置与朝向。
        if (!level().isClientSide) {
            applySpeedCurve()
            if (localTickCounter % 5 == 0) applySeparation()
            if (localTickCounter % 3 == 0) applyPathNoise()
            faceMovement() // 朝移动方向转向，战斗时由攻击目标覆盖
        }
    }

    /**
     * 非战斗时朝移动方向转向（仅服务端执行）。
     *
     * 朝向本应由服务端算好后随移动包同步、客户端插值即可；旧代码在两侧都从本地
     * `deltaMovement` 反推 yaw，而本地速度又被噪声（随机序列不同）与速度曲线（客户端
     * moveAge 恒为 0）污染，于是同一个角度在客户端与服务端之间来回争夺 → 视觉抖动。
     * 另外改用 `Mth.approachDegrees` 限制单 tick 最大转角：原来直接赋值会让噪声引起的
     * 方向抖动被整帧放大，限速后转向平滑、不会再高频抖动。
     */
    private fun faceMovement() {
        if (target != null) return // 战斗中不干预（由 lookControl 注视目标）
        val d = deltaMovement
        val horizSq = d.x * d.x + d.z * d.z
        if (horizSq > 0.0001) {
            // 单 tick 最大转 12°，足够跟上 0.6 的移动速度，同时滤掉逐 tick 的方向毛刺
            yRot = Mth.approachDegrees(yRot, Math.toDegrees(atan2(d.z, d.x)).toFloat() - 90f, 12f)
            // 头部与身体都跟随该权威角度；客户端靠 yBodyRotO/yBodyRot、yHeadRotO/yHeadRot 插值
            yHeadRot = yRot
            yBodyRot = yRot
        }
    }

    override fun performRangedAttack(target: LivingEntity, distanceFactor: Float) {
        if (attackCooldown > 0) return
        attackCooldown = 10

        val fireball = MagicManager.create("fireball") as? Projectile ?: return
        fireball.caster = this
        fireball.wand = droneWand
        val eye = eyePosition
        fireball.position = Vector3f(eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat())
        val center = target.position().add(0.0, target.bbHeight / 2.0, 0.0)
        val dir = center.subtract(eye).normalize()
        fireball.velocity = Vector3f(dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        fireball.spell()
        MagicManager.add(fireball)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        if (source.entity is DroneEntity) return false
        return super.hurtServer(level, source, amount)
    }

    private fun applySpeedCurve() {
        val dx = deltaMovement.x; val dy = deltaMovement.y; val dz = deltaMovement.z
        val lenSq = dx * dx + dy * dy + dz * dz
        if (lenSq < 1e-12) return
        val t = moveAge.toFloat() / rampTicks
        val factor = when {
            t <= 0f -> 0.3
            t >= 1f -> 1.0
            else -> 0.3 + 0.7 * (3.0 * t * t - 2.0 * t * t * t)
        }
        if (kotlin.math.abs(factor - 1.0) < 0.001) return
        deltaMovement = Vec3(dx * factor, dy * factor, dz * factor)
    }

    private fun applySeparation() {
        val nearby = level().getEntitiesOfClass(
            DroneEntity::class.java, boundingBox.inflate(2.0),
        ) { it !== this && it.id < this.id }
        if (nearby.isEmpty()) return
        var px = 0.0; var py = 0.0; var pz = 0.0
        val mx = x; val my = y; val mz = z
        for (other in nearby) {
            val dx = mx - other.x; val dy = my - other.y; val dz = mz - other.z
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq in 0.0001..4.0) {
                val invDist = 1.0 / kotlin.math.sqrt(distSq)
                val force = 0.025 * invDist
                px += dx * invDist * force; py += dy * invDist * force; pz += dz * invDist * force
            }
        }
        val len = px * px + py * py + pz * pz
        if (len > 0.04) { val s = 0.2 / kotlin.math.sqrt(len); px *= s; py *= s; pz *= s }
        addDeltaMovement(Vec3(px, py, pz))
    }

    /**
     * 逐段路径噪声 —— **只能由服务端调用**。
     *
     * 这里用的是实体自带的 `random`：客户端与服务端各自持有一条互不相同的随机序列，
     * 而本方法一旦在两侧都执行，同一架无人机会在两侧得到两条不同轨迹，客户端插值
     * 就不断被服务端同步位置回拉 → 位置抖动。改为仅服务端执行后，噪声属于权威运动，
     * 客户端只负责插值，视觉上仍然是轻微的飘移感。
     */
    private fun applyPathNoise() {
        val dx = deltaMovement.x; val dy = deltaMovement.y; val dz = deltaMovement.z
        if (dx * dx + dy * dy + dz * dz < 0.0025) return
        val r = random
        deltaMovement = Vec3(
            dx + (r.nextDouble() - 0.5) * 0.008,
            dy + (r.nextDouble() - 0.5) * 0.008,
            dz + (r.nextDouble() - 0.5) * 0.008
        )
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

class DroneRandomStrollGoal(
    mob: PathfinderMob,
    speedModifier: Double
) : RandomStrollGoal(mob, speedModifier, 10) {
    override fun canUse(): Boolean = mob.target == null && super.canUse()
}
