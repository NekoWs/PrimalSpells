package work.nekow.primalspells.magic.effect

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.Magic
import work.nekow.primalspells.magic.MagicStatus
import work.nekow.primalspells.wand.Wand

open class BaseEffect {
    lateinit var caster: Entity
    lateinit var wand: Wand
    lateinit var status: MagicStatus
    var magic: Magic? = null

    open fun onTick() { }
    open fun onActive() { }
    open fun onHitEntity(target: Entity) { }
    open fun onHitBlock(pos: Vector3f) { }
}
