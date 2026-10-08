package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.CrystalReaction
import net.minecraft.world.phys.Vec3

/** 水晶引爆：仅激活出生点十六格内属于施法者的水晶。 */
class CrystalDetonation : Projectile() {
    override val id = "crystal_detonation"
    init { mana = 5.0; delay = 20; maxAge = 1; speed = 0f }
    override fun onSpell() {
        CrystalReaction.detonateOwned(caster, Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()))
    }
}
