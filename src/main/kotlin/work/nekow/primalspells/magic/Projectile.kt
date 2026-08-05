package work.nekow.primalspells.magic

import work.nekow.primalspells.magic.effect.Move
import work.nekow.primalspells.magic.effect.Position
import work.nekow.primalspells.magic.effect.Velocity

abstract class Projectile: Magic() {
    var status: MagicStatus = MagicStatus()

    override var cast: Int = 1

    override fun spell() {
        val spellPos = caster.eyePosition.add(0.0, -0.2, 0.0)
        effects += Position(spellPos.toVector3f())
        effects += Move()
        effects += Velocity(caster.lookAngle.toVector3f())

        effects.forEach {
            it.status = status
        }
        super.spell()
    }

    override fun onSpell() {
    }

    abstract override fun clone(): Projectile
}