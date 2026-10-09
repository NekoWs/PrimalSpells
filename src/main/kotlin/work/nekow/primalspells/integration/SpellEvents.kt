package work.nekow.primalspells.integration

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.wand.Wand

/** 全部在服务端NeoForge.EVENT_BUS，同步派发；监听器不得在当前回调递归施放同一根杖。 */
object SpellEvents {
    class BeforeCast(val caster: Entity, val wand: Wand) : Event(), ICancellableEvent
    class AfterCast(val caster: Entity, val wandId: String, val manaBefore: Double, val manaAfter: Double, val delay: Double, val recharge: Double) : Event()
    class BeforeImpact(val caster: Entity, val target: Entity, val type: SpellDamageType, var amount: Double, val direction: Vec3) : Event(), ICancellableEvent
    class AfterImpact(val caster: Entity, val target: Entity, val type: SpellDamageType, val amount: Double) : Event()
    class StatusChanged(val target: Entity, val status: String, val active: Boolean, val ticks: Int) : Event()
    /** 探测/搬运/救援每个目标分别询问，整合包可在这里接入领地/队伍权限。 */
    class UtilityAction(val caster: Entity, val target: Entity, val action: String) : Event(), ICancellableEvent
}
