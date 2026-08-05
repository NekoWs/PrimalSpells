package work.nekow.primalspells.magic.effect

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect

class Trigger : BaseEffect() {
    override fun onHitEntity(target: Entity) {
        castPayload()
    }

    override fun onHitBlock(pos: Vector3f) {
        castPayload()
    }

    private fun castPayload() {
        val proj = magic as? Projectile ?: return
        val spell = proj as? TriggerSpell ?: return
        val chain = spell.payload ?: return

        val allEffects = arrayListOf<BaseEffect>()
        for (mod in chain.modifiers) {
            mod.caster = proj.caster
            mod.wand = proj.wand
            mod.onSpell()
            allEffects.addAll(mod.effects)
        }

        val child = chain.projectile.clone()
        child.caster = proj.caster
        child.wand = proj.wand
        child.effects.addAll(0, allEffects)
        child.spell()
        child.status.pos = proj.status.pos
        child.status.velocity = proj.status.velocity
        MagicManager.add(child)
    }
}
