package work.nekow.primalspells.entity

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.RangedAttackMob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import kotlin.math.sqrt

/**
 * 无人机攻击目标 —— 周期性搜索非无人机、非施法者的实体，
 * 优先敌对生物和玩家。攻击时每 10～17 tick 做随机方向平移保持机动。
 */
class DroneAttackGoal(
    private val drone: DroneEntity,
    private val speedModifier: Double,
    private val attackInterval: Int,
    private val attackRadius: Float
) : Goal() {

    private var attackTime = -1
    private var seeTime = 0

    /** 战斗平移计时器，随机 10～17 tick 触发一次 */
    private var strafeTimer = 0
    /** 当前平移目标坐标 */
    private var strafeTarget: Vec3? = null

    init {
        this.flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        val target = findValidTarget() ?: return false
        drone.target = target
        return true
    }

    override fun canContinueToUse(): Boolean {
        val target = drone.target ?: return false
        return target.isAlive && isValidTarget(target)
    }

    override fun start() {
        attackTime = 0
        strafeTimer = 0
        strafeTarget = null
    }

    override fun stop() {
        drone.target = null
        seeTime = 0
        attackTime = -1
        strafeTarget = null
    }

    override fun tick() {
        val target = drone.target ?: return

        val canSee = drone.hasLineOfSight(target)
        if (canSee) seeTime++ else seeTime = 0

        val distSq = drone.distanceToSqr(target)
        val dist = sqrt(distSq).toFloat()

        // 注视目标
        drone.lookControl.setLookAt(target, 30f, 30f)

        if (distSq <= attackRadius * attackRadius.toDouble() && seeTime >= 5) {
            // 在攻击范围内：战斗平移机动
            updateStrafe(target)
        } else {
            // 超出攻击范围：追踪靠近
            drone.navigation.moveTo(target, speedModifier)
            strafeTarget = null
        }

        // 攻击
        attackTime--
        if (attackTime <= 0 && seeTime >= 5) {
            (drone as RangedAttackMob).performRangedAttack(target, dist / attackRadius)
            attackTime = attackInterval
        }
    }

    /**
     * 战斗平移：每 10～17 tick 选取一个随机方向偏移 2～4 格，
     * 让无人机在攻击时保持机动而非静止不动。
     */
    private fun updateStrafe(target: LivingEntity) {
        strafeTimer--
        if (strafeTimer > 0 && strafeTarget != null) {
            // 当前平移未结束，继续向目标位置移动
            return
        }
        // 重置计时器（10～17 tick 随机间隔）
        strafeTimer = drone.random.nextInt(10, 18)
        // 以当前无人机位置为中心，随机偏移 2～4 格
        val offsetX = (drone.random.nextDouble() - 0.5) * 4.0 // -2 ～ +2
        val offsetY = (drone.random.nextDouble() - 0.5) * 2.0 // -1 ～ +1 高度变化
        val offsetZ = (drone.random.nextDouble() - 0.5) * 4.0
        strafeTarget = drone.position().add(offsetX, offsetY, offsetZ)
        drone.navigation.moveTo(strafeTarget!!.x, strafeTarget!!.y, strafeTarget!!.z, speedModifier)
    }

    private fun findValidTarget(): LivingEntity? {
        val entities = drone.level().getEntitiesOfClass(
            LivingEntity::class.java,
            drone.boundingBox.inflate(attackRadius.toDouble()),
        ) { entity -> isValidTarget(entity) }
        return entities.sortedWith(
            compareBy<LivingEntity> { getPriority(it) }
                .thenBy { drone.distanceToSqr(it) }
        ).firstOrNull()
    }

    private fun getPriority(entity: LivingEntity): Int = when {
        entity is Enemy -> 0
        entity is Player -> 1
        else -> 2
    }

    private fun isValidTarget(entity: LivingEntity): Boolean {
        if (entity === drone) return false
        if (entity is DroneEntity) return false
        if (drone.owner != null && entity.uuid == drone.owner) return false
        if (entity is Player && (entity.isCreative || entity.isSpectator)) return false
        return entity.isAlive
    }

    override fun requiresUpdateEveryTick(): Boolean = true
}
