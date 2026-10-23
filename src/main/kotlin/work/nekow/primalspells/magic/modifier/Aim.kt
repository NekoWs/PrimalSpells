package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.visual.AimingFx
import work.nekow.primalspells.magic.effect.visual.WandCastingFx
import work.nekow.primalspells.network.AimTargetPayload
import work.nekow.primalspells.wand.WandAim

/** 持杖被动：显示中央直线的首个接点，不模拟散射、追踪或反弹后的弹道。 */
class Aim : Modifier() {
    override val id = "aim"
    override var needInventory = true
    private var fx: AimingFx? = null
    private var holder: Entity? = null
    var lastHit: WandAim.Sight? = null
        private set
    val particleCount: Int get() = fx?.particleCount ?: 0
    internal val sampleId: java.util.UUID? get() = fx?.sampleId

    override fun onInventoryTick() {
        if (wand.loadedMagics.firstOrNull { it.id == id } !== this) return
        if (caster.level() !is ServerLevel || !caster.isAlive || caster.isSpectator) { retire(); return }
        if (holder !== caster || holder?.level() !== fx?.level) retire()
        val origin = WandCastingFx.origin(caster, wand)
        val sight = WandAim.raycast(caster, origin, WandAim.direction(caster, origin), RANGE)
        holder = caster; lastHit = sight
        val visual = fx ?: AimingFx(caster.level() as ServerLevel, origin, sight.point).also { fx = it }
        visual.update(origin, sight.point)
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, AimTargetPayload(caster.uuid, wand.id, sight.entity?.uuid))
    }

    override fun onPassiveIdle() = retire()
    override fun onUnloaded() = retire()

    private fun retire() {
        val previous = holder
        if (previous != null && previous.level() is ServerLevel)
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(previous, AimTargetPayload(previous.uuid, wand.id, null))
        fx?.retire(); fx = null; holder = null; lastHit = null
    }

    companion object { const val RANGE = 64.0 }
}
