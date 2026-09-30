package work.nekow.primalspells.magic.core

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.primalspells.magic.projectile.DroneSwarm
import work.nekow.primalspells.magic.projectile.DelayedCastProjectile
import work.nekow.primalspells.magic.projectile.Fireball
import work.nekow.primalspells.magic.projectile.immobile.LargeExplosion
import work.nekow.primalspells.magic.projectile.Laser
import work.nekow.primalspells.magic.projectile.immobile.SmallExplosion
import work.nekow.primalspells.magic.projectile.immobile.GravityFieldProjectile
import work.nekow.primalspells.magic.projectile.immobile.BlackHoleProjectile
import work.nekow.primalspells.magic.projectile.TriggerFireball
import work.nekow.primalspells.magic.projectile.TeleportProjectile
import work.nekow.primalspells.magic.modifier.*
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.Deferred

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
        // 延迟重音（塌缩爆闪、波环落地溅射…）也在这里推进：它们必须在效果死掉之后仍然到点触发
        Deferred.tick()
    }

    init {
        register { Fireball() }
        register { TriggerFireball() }
        register { DelayedCastProjectile() }
        register { DroneSwarm() }
        register { DamageBoost() }
        register { Gravity() }
        register { MoreBounce() }
        register { DoubleCast() }
        register { SpeedBoost() }
        register { ExtendedLifetime() }
        register { Laser() }
        register { Piercing() }
        register { BounceExplosion() }
        register { Homing() }
        register { BounceDuplicate() }
        register { GradualAccelerate() }
        register { GradualDecelerate() }
        register { RandomPath() }
        register { Reciprocate() }
        register { Serpentine() }
        register { ManaInjection() }
        register { Diffusion() }
        register { Implosion() }
        register { Volatile() }
        register { ClusterBomb() }
        register { ExplosionRemove() }
        register { ExplosionProjectile() }
        register { SmallExplosion() }
        register { LargeExplosion() }
        register { GravityFieldProjectile() }
        register { BlackHoleProjectile() }
        register { SelfOrbit() }
        register { SphereOrbit() }
        register { TeleportProjectile() }
        register { QuantumEntanglement() }
        register { EnergyShield() }
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Pre) {
        tick()
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        projectiles.clear()
        Deferred.clear()
    }
}
