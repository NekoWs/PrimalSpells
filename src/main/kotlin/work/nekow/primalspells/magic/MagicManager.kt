package work.nekow.primalspells.magic

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.primalspells.PrimalSpells.Companion.overlay

object MagicManager {
    val projectiles = arrayListOf<Projectile>()
    private val pendingAdd = arrayListOf<Projectile>()

    fun add(projectile: Projectile) {
        pendingAdd.add(projectile)
    }

    fun tick() {
        projectiles.addAll(pendingAdd)
        pendingAdd.clear()

        if (projectiles.isNotEmpty()) {
            val first = projectiles.first()
            first.caster.overlay("Projectiles Count: ${projectiles.size}")
        }

        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val it = iter.next()
            if (!it.alive) {
                iter.remove()
                continue
            }
            it.tick()
            it.status.age++
            if (it.status.age >= it.maxAge) iter.remove()
        }
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Pre) {
        tick()
    }
}
