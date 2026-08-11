package work.nekow.primalspells.entity

import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.player.Player
import java.util.EnumSet

/**
 * 无人机跟随施法者目标 —— 无攻击目标时跟随 owner 玩家，
 * 保持 3～5 格距离不作攻击。
 */
class DroneFollowOwnerGoal(
    private val drone: DroneEntity,
    private val speedModifier: Double
) : Goal() {

    private var owner: Player? = null

    init {
        this.flags = EnumSet.of(Flag.MOVE)
    }

    /** 仅在无攻击目标且有合法 owner 时执行 */
    override fun canUse(): Boolean {
        val target = drone.target
        if (target != null && target.isAlive) return false // 有攻击目标时不跟随
        val uuid = drone.owner ?: return false
        val player = drone.level().getPlayerByUUID(uuid) ?: return false
        if (!player.isAlive) return false
        if (drone.distanceToSqr(player) < 3.0 * 3.0) return false // 已足够近
        owner = player
        return true
    }

    override fun canContinueToUse(): Boolean {
        val target = drone.target
        if (target != null && target.isAlive) return false // 有攻击目标则中断
        val player = owner ?: return false
        return player.isAlive && drone.distanceToSqr(player) > 2.0 * 2.0
    }

    override fun start() {
        // 开始寻路
    }

    override fun stop() {
        owner = null
        drone.navigation.stop()
    }

    override fun tick() {
        val player = owner ?: return
        // 注视 owner
        drone.lookControl.setLookAt(player, 30f, 30f)
        // 保持在 4 格左右距离
        val dist = drone.distanceTo(player)
        if (dist > 4.0) {
            drone.navigation.moveTo(player, speedModifier) // 靠近
        } else {
            drone.navigation.stop() // 已足够近
        }
    }

    override fun requiresUpdateEveryTick(): Boolean = true
}
