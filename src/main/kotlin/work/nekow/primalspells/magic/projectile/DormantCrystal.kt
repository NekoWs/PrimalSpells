package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.geometry.ConstantAcceleration
import work.nekow.primalspells.magic.effect.lifecycle.CrystalReaction
import org.joml.Vector3f

/** 可投掷并粘附方块的休眠水晶，寿命三十秒；数量不做每人截断。 */
class DormantCrystal : Projectile() {
    override val id = "dormant_crystal"
    override val bodyColor = Color.ofInt(114, 216, 185)
    init {
        mana = 20.0; delay = 30; maxAge = 600; speed = 0.55f
        status.explosionRadius = 3f; status.explosionDamage = 12.0
        effects += ConstantAcceleration(Vector3f(0f, -0.025f, 0f))
        effects += CrystalReaction()
    }
}
