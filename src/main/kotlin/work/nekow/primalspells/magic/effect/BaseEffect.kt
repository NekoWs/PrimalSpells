package work.nekow.primalspells.magic.effect

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.MagicStatus
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.utils.HitResult
import work.nekow.primalspells.wand.Wand

open class BaseEffect {
    lateinit var caster: Entity
    lateinit var wand: Wand
    lateinit var status: MagicStatus
    lateinit var projectile: Projectile

    open fun onTick() { }
    open fun onActive() { }
    open fun onHitEntity(target: Entity) { }
    open fun onHitBlock(pos: Vector3f) { }
    open fun onHit(result: HitResult) { }
}
