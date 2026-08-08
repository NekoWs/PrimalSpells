package work.nekow.primalspells.magic.effect

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.Magic

class Trigger(
    val magics: ArrayList<Magic>,
    val hitEntity: Boolean = true,
    val hitBlock: Boolean = true
) : BaseEffect() {
    override fun onHitEntity(target: Entity) {
        if (hitEntity) start()
    }

    override fun onHitBlock(pos: Vector3f) {
        if (hitBlock) start()
    }

    private fun start() {
        magics.forEach {
            it.caster = caster
            // TODO: 触发施法
        }
    }
}
