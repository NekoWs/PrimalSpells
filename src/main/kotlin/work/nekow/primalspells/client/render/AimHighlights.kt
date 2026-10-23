package work.nekow.primalspells.client.render

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.network.AimTargetPayload
import java.util.UUID

/** 按施法者/法杖合并短租约；多束瞄准互不撤销，也不移除原版或其它模组的发光。 */
object AimHighlights {
    private data class Lease(val target: UUID, var remaining: Int = 5)
    private val leases = hashMapOf<Pair<UUID, String>, Lease>()
    private var level: ClientLevel? = null

    fun accept(payload: AimTargetPayload) {
        val current = Minecraft.getInstance().level ?: return
        if (level !== current) { clear(); level = current }
        val key = payload.casterId to payload.wandId
        if (payload.targetId == null) leases.remove(key) else leases[key] = Lease(payload.targetId)
    }

    fun tick() {
        if (level !== Minecraft.getInstance().level) { clear(); return }
        leases.entries.removeIf { --it.value.remaining <= 0 }
    }

    @JvmStatic fun highlights(entity: Entity): Boolean = entity.isAlive && entity.level() === level &&
        leases.values.any { it.target == entity.uuid }

    fun clear() { leases.clear(); level = null }
}
