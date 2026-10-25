package work.nekow.primalspells.registry

import work.nekow.primalspells.magic.action.*
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.modifier.*
import work.nekow.primalspells.magic.projectile.ChainBolt
import work.nekow.primalspells.magic.projectile.Chainsaw
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.magic.projectile.DelayedCastProjectile
import work.nekow.primalspells.magic.projectile.DormantCrystal
import work.nekow.primalspells.magic.projectile.DroneBolt
import work.nekow.primalspells.magic.projectile.DroneSwarm
import work.nekow.primalspells.magic.projectile.Fireball
import work.nekow.primalspells.magic.projectile.HealingBolt
import work.nekow.primalspells.magic.projectile.Laser
import work.nekow.primalspells.magic.projectile.PlasmaBeamProjectile
import work.nekow.primalspells.magic.projectile.Sawblade
import work.nekow.primalspells.magic.projectile.TeleportProjectile
import work.nekow.primalspells.magic.projectile.TriggerFireball
import work.nekow.primalspells.magic.projectile.immobile.*

/** 内置法术内容清单；公共入口先注册工厂，再创建依赖此表的物品。 */
object ModSpells {
    fun registerAll() {
        SpellRegistry.register { ReducedLifetime() }
        SpellRegistry.register { Alpha() }
        SpellRegistry.register { Gamma() }
        SpellRegistry.register { Tau() }
        SpellRegistry.register { Omega() }
        SpellRegistry.register { Mu() }
        SpellRegistry.register { Phi() }
        SpellRegistry.register { Sigma() }
        SpellRegistry.register { Zeta() }
        SpellRegistry.register { VoidEscape() }
        SpellRegistry.register { BloodMagic() }
        SpellRegistry.register { WandRefresh() }
        SpellRegistry.register { SurveyPulse() }
        SpellRegistry.register { ItemPull() }
        SpellRegistry.register { FeatherWard() }
        SpellRegistry.register { Fireball() }
        SpellRegistry.register { DivideTwo() }
        SpellRegistry.register { DivideThree() }
        SpellRegistry.register { DivideFive() }
        SpellRegistry.register { DivideTen() }
        SpellRegistry.register { CastAll() }
        SpellRegistry.register { AddTrigger() }
        SpellRegistry.register { AddTimer() }
        SpellRegistry.register { AddExpiration() }
        SpellRegistry.register { TripleCast() }
        SpellRegistry.register { QuadrupleCast() }
        SpellRegistry.register { QuintupleCast() }
        SpellRegistry.register { ReduceRecharge() }
        SpellRegistry.register { Torch() }
        SpellRegistry.register { Light() }
        SpellRegistry.register { Sawblade() }
        SpellRegistry.register { HealingBolt() }

        SpellRegistry.register { DroneBolt() }
        SpellRegistry.register { TriggerFireball() }
        SpellRegistry.register { DelayedCastProjectile() }
        SpellRegistry.register { DroneSwarm() }
        SpellRegistry.register { DamageBoost() }
        SpellRegistry.register { Gravity() }
        SpellRegistry.register { MoreBounce() }
        SpellRegistry.register { DoubleCast() }
        SpellRegistry.register { SpeedBoost() }
        SpellRegistry.register { ExtendedLifetime() }
        SpellRegistry.register { Laser() }
        SpellRegistry.register { Chainsaw() }
        SpellRegistry.register { ConcentratedLight() }
        SpellRegistry.register { RecoilDamper() }
        SpellRegistry.register { RecoilBoost() }
        SpellRegistry.register { Piercing() }
        SpellRegistry.register { BounceExplosion() }
        SpellRegistry.register { Homing() }
        SpellRegistry.register { OmnidirectionalScatter() }
        SpellRegistry.register { BounceDuplicate() }
        SpellRegistry.register { GradualAccelerate() }
        SpellRegistry.register { GradualDecelerate() }
        SpellRegistry.register { RandomPath() }
        SpellRegistry.register { Reciprocate() }
        SpellRegistry.register { Serpentine() }
        SpellRegistry.register { ManaInjection() }
        SpellRegistry.register { Diffusion() }
        SpellRegistry.register { Implosion() }
        SpellRegistry.register { Volatile() }
        SpellRegistry.register { ExplosionRemove() }
        SpellRegistry.register { ExplosionProjectile() }
        SpellRegistry.register { SmallExplosion() }
        SpellRegistry.register { LargeExplosion() }
        SpellRegistry.register { GravityFieldProjectile() }
        SpellRegistry.register { BlackHoleProjectile() }
        SpellRegistry.register { SelfOrbit() }
        SpellRegistry.register { TeleportProjectile() }
        SpellRegistry.register { QuantumEntanglement() }
        SpellRegistry.register { EnergyShield() }
        SpellRegistry.register { DormantCrystal() }
        SpellRegistry.register { CrystalDetonation() }
        SpellRegistry.register { ChainBolt() }
        SpellRegistry.register { LongDistanceCast() }
        SpellRegistry.register { PlasmaBeamProjectile() }
        SpellRegistry.register { RepulsionFieldProjectile() }
        SpellRegistry.register { FreezeCharge() }
    }
}
