package work.nekow.primalspells.magic.projectile.immobile

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.integration.SpellEvents
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.visual.Trajectory

/** 主动生活法术：无方块改写，逐目标授权，不把玩家背包写入作为拾取捷径。 */
abstract class UtilityPulse : Projectile() {
    init { speed = 0f; maxAge = 6; collisionEnabled = false; motionEnabled = false; delay = 8; effects += Trajectory(Color.ofInt(126, 236, 204)) }
    protected fun visible(level: ServerLevel, from: Vec3, to: Vec3) = level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).type == HitResult.Type.MISS
    protected fun allowed(target: net.minecraft.world.entity.Entity) = !NeoForge.EVENT_BUS.post(SpellEvents.UtilityAction(caster, target, id)).isCanceled
}
