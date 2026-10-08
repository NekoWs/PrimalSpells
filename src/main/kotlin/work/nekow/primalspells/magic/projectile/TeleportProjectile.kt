package work.nekow.primalspells.magic.projectile

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.visual.Trajectory
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.util.Lore

/**
 * 传送魔弹 —— 命中实体/方块，或自然消亡（到达 maxAge）时，将施法者传送到该位置。
 *
 * - 命中：传送到碰撞点（方块命中时取方块表面外侧一点，避免卡进方块）
 * - 消亡：传送到投射物最终位置
 * - 仅传送一次（命中后死亡不会重复传送）
 */
class TeleportProjectile : Projectile() {

    override val id = "teleport"
    override val bodyColor = Color.of(0.6f, 0.3f, 1f)

    /** 是否已执行传送（防止命中后死亡重复传送） */
    private var teleported = false

    init {
        mana = 4.0
        delay = 0
        recharge = 0
        maxAge = 100
        speed = 1.0f
        hitRadius = 0.2
        effects += Trajectory(Color.of(0.6f, 0.3f, 1f))
    }

    override fun onHit(result: HitResult) {
        teleportTo(result.pos)
    }

    override fun onDie() {
        teleportTo(status.pos)
    }

    private fun teleportTo(pos: Vector3f) {
        if (teleported) return
        teleported = true
        val level = caster.level() as? ServerLevel ?: return

        val x = pos.x.toDouble()
        val y = pos.y.toDouble()
        val z = pos.z.toDouble()

        val manager = work.nekow.particledrawing.api.ParticleManager.of(level)
        val from = caster.position().add(0.0, caster.eyeHeight.toDouble(), 0.0)
        val to = net.minecraft.world.phys.Vec3(x, y, z)
        val color = Color.ofInt(182, 132, 255)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.ring(manager, from, net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0), 0.35, color, 12, 3f)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(manager, from, color, 96, 0.12, 12)
        caster.teleportTo(level, x, y, z, emptySet(), caster.yRot, caster.xRot, true)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.ring(manager, to, net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0), 0.35, color, 12, 3f)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(manager, to, color, 128, 0.2, 16)

        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f)
    }

    override fun initLore() {
        super.initLore()
        lore(Lore.DESCRIPTION)
    }
}
