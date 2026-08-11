package work.nekow.primalspells.entity

import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.player.Player
import java.util.EnumSet

class DroneFollowOwnerGoal(
    private val drone: DroneEntity,
    private val speedModifier: Double
) : Goal() {

    private var owner: Player? = null

    init {
        this.flags = EnumSet.of(Flag.MOVE)
    }

    override fun canUse(): Boolean {
        if (drone.target?.isAlive == true) return false
        val uuid = drone.owner ?: return false
        val player = drone.level().getPlayerByUUID(uuid) ?: return false
        if (!player.isAlive) return false
        if (drone.distanceToSqr(player) < 3.0 * 3.0) return false
        owner = player
        return true
    }

    override fun canContinueToUse(): Boolean {
        if (drone.target?.isAlive == true) return false
        val player = owner ?: return false
        return player.isAlive && drone.distanceToSqr(player) > 2.0 * 2.0
    }

    override fun start() {}

    override fun stop() {
        owner = null
        drone.navigation.stop()
    }

    override fun tick() {
        val player = owner ?: return
        drone.lookControl.setLookAt(player, 30f, 30f)
        val dist = drone.distanceTo(player)
        if (dist > 4.0) {
            drone.navigation.moveTo(player, speedModifier)
        } else {
            drone.navigation.stop()
        }
    }

    override fun requiresUpdateEveryTick(): Boolean = true
}
