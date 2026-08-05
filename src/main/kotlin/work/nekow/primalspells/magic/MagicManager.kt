package work.nekow.primalspells.magic

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

object MagicManager {
    val magics = arrayListOf<Projectile>()
    private val pendingAdd = arrayListOf<Projectile>()

    fun add(projectile: Projectile) {
        pendingAdd.add(projectile)
    }

    fun tick() {
        magics.addAll(pendingAdd)
        pendingAdd.clear()

        val iter = magics.iterator()
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
