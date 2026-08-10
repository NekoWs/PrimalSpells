package work.nekow.primalspells.magic

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.primalspells.magic.projectile.Fireball
import work.nekow.primalspells.magic.projectile.TriggerFireball
import work.nekow.primalspells.magic.revise.*

object MagicManager {
    val registry = mutableMapOf<String, () -> Magic>()

    fun register(factory: () -> Magic) {
        registry[factory().id] = factory
    }

    fun create(id: String): Magic? = registry[id]?.invoke()?.also { it.initLore() }

    val projectiles = arrayListOf<Projectile>()
    private val pendingAdd = arrayListOf<Projectile>()

    fun add(projectile: Projectile) {
        pendingAdd.add(projectile)
    }

    fun tick() {
        projectiles.addAll(pendingAdd)
        pendingAdd.clear()

        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val it = iter.next()
            if (!it.alive) {
                it.die()
                iter.remove()
                continue
            }
            it.tick()
            it.status.age++
            if (it.status.age >= it.maxAge) iter.remove()
        }
    }

    init {
        register { Fireball() }
        register { TriggerFireball() }
        register { DamageBoost() }
        register { Gravity() }
        register { Bounce() }
        register { DoubleCast() }
        register { SpeedBoost() }
        register { ExtendedLifetime() }
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Pre) {
        tick()
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        projectiles.clear()
    }
}
