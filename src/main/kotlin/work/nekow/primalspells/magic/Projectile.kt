package work.nekow.primalspells.magic

import org.joml.Vector3f
import work.nekow.primalspells.magic.effect.Move
import work.nekow.primalspells.magic.effect.Position
import work.nekow.primalspells.magic.effect.Velocity

abstract class Projectile: Magic() {
    var status: MagicStatus = MagicStatus()

    override var cast: Int = 0

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

    override fun onSpell() {
    }

    abstract override fun clone(): Projectile
}