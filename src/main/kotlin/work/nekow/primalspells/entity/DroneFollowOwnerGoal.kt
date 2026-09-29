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

    /**
     * 仅在服务端接管跟随。客户端本地寻路/位置并非权威，若这里也返回 true，
     * 客户端的 `lookControl`/`navigation` 会与「服务端同步 + 插值」的朝向和位置互相争夺，
     * 表现为无人机跟随时的抖动，因此客户端一律不启动该 goal。
     */
    override fun canUse(): Boolean {
        if (drone.level().isClientSide) return false
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
