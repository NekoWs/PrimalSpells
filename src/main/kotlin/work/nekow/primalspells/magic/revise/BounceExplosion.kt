package work.nekow.primalspells.magic.revise

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.joml.Vector3f
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

class BounceExplosion : Revise() {
    override val id = "bounce_explosion"

    init {
        mana = 5.0
        delay = 1
        effects += object : BaseEffect() {

            override fun onHitBlock(pos: Vector3f) {
                if (!projectile.alive) return

                val level = caster.level() as? ServerLevel ?: return

                level.explode(
                    caster,
                    null,
                    null,
                    pos.x.toDouble(),
                    pos.y.toDouble(),
                    pos.z.toDouble(),
                    2.0f,
                    false,
                    Level.ExplosionInteraction.NONE
                )
            }
        }
    }
}
