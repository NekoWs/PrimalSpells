package work.nekow.primalspells.magic

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.effect.Move
import work.nekow.primalspells.magic.effect.Position
import work.nekow.primalspells.magic.effect.Velocity

abstract class Projectile: Magic() {
    var status: MagicStatus = MagicStatus()

    override var cast: Int = 0

    var maxAge: Int = 0
    var alive: Boolean = true

    lateinit var position: Vector3f
    lateinit var velocity: Vector3f

    override fun spell() {
        effects += Position(position)
        effects += Move()
        effects += Velocity(velocity)

        effects.forEach {
            it.projectile = this
            it.status = status
        }
        super.spell()
    }

    fun hitEntity(entity: Entity) {
        effects.forEach { it.onHitEntity(entity) }
        onHitEntity(entity)
    }

    fun hitBlock(pos: Vector3f) {
        effects.forEach { it.onHitBlock(pos) }
        onHitBlock(pos)
    }

    open fun onHitEntity(entity: Entity) { }
    open fun onHitBlock(pos: Vector3f) { }

    override fun onSpell() { }

    override fun clone(): Projectile = super.clone() as Projectile
}