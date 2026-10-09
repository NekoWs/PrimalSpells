package work.nekow.primalspells.magic.core

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.magic.projectile.Chainsaw
import work.nekow.primalspells.magic.projectile.DroneSwarm
import work.nekow.primalspells.magic.projectile.DelayedCastProjectile
import work.nekow.primalspells.magic.projectile.Fireball
import work.nekow.primalspells.magic.projectile.DroneBolt
import work.nekow.primalspells.magic.projectile.immobile.LargeExplosion
import work.nekow.primalspells.magic.projectile.Laser
import work.nekow.primalspells.magic.projectile.immobile.SmallExplosion
import work.nekow.primalspells.magic.projectile.immobile.GravityFieldProjectile
import work.nekow.primalspells.magic.projectile.immobile.BlackHoleProjectile
import work.nekow.primalspells.magic.projectile.TriggerFireball
import work.nekow.primalspells.magic.projectile.TeleportProjectile
import work.nekow.primalspells.magic.modifier.*
import work.nekow.primalspells.magic.projectile.DormantCrystal
import work.nekow.primalspells.magic.projectile.ChainBolt
import work.nekow.primalspells.magic.projectile.PlasmaBeamProjectile
import work.nekow.primalspells.magic.projectile.immobile.CrystalDetonation
import work.nekow.primalspells.magic.projectile.immobile.RepulsionFieldProjectile
import work.nekow.primalspells.magic.effect.lifecycle.FrozenStatus
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
        FrozenStatus.tick()
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
            if (it.status.age >= it.maxAge && it.alive) { it.expire(); it.alive = false }
        }
        projectiles.addAll(pendingAdd)
        pendingAdd.clear()
        work.nekow.primalspells.magic.effect.lifecycle.SpellInteractions.tick(projectiles.toList())
        // 延迟重音（塌缩爆闪、波环落地溅射…）也在这里推进：它们必须在效果死掉之后仍然到点触发
        Deferred.tick()
    }

    init {
        register { Fireball() }
        register { DroneBolt() }
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
        register { Chainsaw() }
        register { ConcentratedLight() }
        register { RecoilDamper() }
        register { RecoilBoost() }
        register { Piercing() }
        register { BounceExplosion() }
        register { Homing() }
        register { OmnidirectionalScatter() }
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
        register { ExplosionRemove() }
        register { ExplosionProjectile() }
        register { SmallExplosion() }
        register { LargeExplosion() }
        register { GravityFieldProjectile() }
        register { BlackHoleProjectile() }
        register { SelfOrbit() }
        register { TeleportProjectile() }
        register { QuantumEntanglement() }
        register { EnergyShield() }
        register { DormantCrystal() }
        register { CrystalDetonation() }
        register { ChainBolt() }
        register { LongDistanceCast() }
        register { PlasmaBeamProjectile() }
        register { RepulsionFieldProjectile() }
        register { FreezeCharge() }
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Pre) {
        tick()
    }

    /** 高配特效档：使用PD公开配置提高容量，避免完整晶面被默认可见粒子预算截断。 */
    @SubscribeEvent
    fun onServerStarted(event: net.neoforged.neoforge.event.server.ServerStartedEvent) {
        val config = work.nekow.particledrawing.config.ParticleDrawingConfig.SERVER
        config.maxParticlesPerDimension.set(1_000_000)
        config.maxParticlesPerPlayer.set(100_000)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        FrozenStatus.clear()
        projectiles.clear()
        Deferred.clear()
    }
}
