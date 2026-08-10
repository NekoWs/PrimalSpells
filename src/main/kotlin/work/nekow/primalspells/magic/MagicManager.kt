package work.nekow.primalspells.magic

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.primalspells.magic.projectile.Fireball
import work.nekow.primalspells.magic.projectile.Laser
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
            if (it.status.age >= it.maxAge) it.alive = false
        }
        projectiles.addAll(pendingAdd)
        pendingAdd.clear()
    }

    init {
        register { Fireball() }
        register { TriggerFireball() }
        register { DamageBoost() }
        register { Gravity() }
        register { MoreBounce() }
        register { DoubleCast() }
        register { SpeedBoost() }
        register { ExtendedLifetime() }
        register { SpeedGetter() }
        register { Laser() }
        register { Piercing() }
        register { BounceExplosion() }
        register { Homing() }
        register { BounceDuplicate() }
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
