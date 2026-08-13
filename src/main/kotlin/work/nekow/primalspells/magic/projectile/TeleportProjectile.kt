package work.nekow.primalspells.magic.projectile

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.utils.HitResult
import work.nekow.primalspells.utils.Lore

/**
 * 传送魔弹 —— 命中实体/方块，或自然消亡（到达 maxAge）时，将施法者传送到该位置。
 *
 * - 命中：传送到碰撞点（方块命中时取方块表面外侧一点，避免卡进方块）
 * - 消亡：传送到投射物最终位置
 * - 仅传送一次（命中后死亡不会重复传送）
 */
class TeleportProjectile : Projectile() {

    override val id = "teleport"

    /** 是否已执行传送（防止命中后死亡重复传送） */
    private var teleported = false

    init {
        mana = 4.0
        delay = 1
        recharge = 1
        maxAge = 100
        speed = 1.0f
        hitRadius = 0.2
        effects += Trajectory(ParticleTypes.PORTAL)
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

        // 起点粒子（施法者原位置）
        level.sendParticles(
            ParticleTypes.PORTAL,
            caster.x, caster.y + caster.eyeHeight, caster.z,
            32, 0.3, 0.3, 0.3, 0.05
        )

        caster.teleportTo(level, x, y, z, emptySet(), caster.yRot, caster.xRot, true)

        // 终点粒子（传送目标位置）
        level.sendParticles(
            ParticleTypes.PORTAL,
            x, y, z,
            32, 0.3, 0.3, 0.3, 0.05
        )

        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f)
    }

    override fun initLore() {
        super.initLore()
        lore(Lore.DESCRIPTION)
    }
}
