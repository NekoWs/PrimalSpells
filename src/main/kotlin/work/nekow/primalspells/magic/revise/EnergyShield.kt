package work.nekow.primalspells.magic.revise

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Vector3f
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.network.SyncShieldPayload
import work.nekow.primalspells.utils.Lore
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 能量盾（被动法术）：编入法杖后，只要法杖位于玩家手上（主手/副手）即自动生效。
 *
 * 为持有者提供一层能量护盾：进入护盾范围的敌方法术会被弹开（反射其速度），
 * 每次弹开消耗耐久；耐久归零后护盾破碎，暂停恢复一段时间（[breakPauseTicks]），
 * 之后以 [regenPerSecond] 的速度恢复耐久。
 *
 * 不弹开持有者自己施放的法术（投射物出生点在施法者面前，会被瞬间弹回）。
 */
class EnergyShield : Revise() {
    override val id = "energy_shield"

    /** 最大耐久 */
    var maxDurability: Double = 100.0

    /** 耐久回复速度（每秒） */
    var regenPerSecond: Double = 10.0

    /** 破盾后暂停恢复时长（tick） */
    var breakPauseTicks: Int = 50

    /** 每次弹开法术消耗的耐久 */
    var bounceCost: Double = 5.0

    /** 弹开检测半径（玩家包围盒外扩距离） */
    var shieldRadius: Double = 1.5

    /** 当前耐久 */
    var durability: Double = maxDurability

    /** 破盾暂停剩余 tick */
    var pauseTicks: Int = 0

    override var needInventory: Boolean = true

    override fun onInventoryTick() {
        val player = caster
        if (!player.isAlive) return
        val level = player.level()
        if (level !is ServerLevel) return

        // 耐久回复（破盾暂停期间不回复）
        if (pauseTicks > 0) {
            pauseTicks--
        } else {
            durability = min(maxDurability, durability + regenPerSecond / 20.0)
        }
        val shieldUp = durability > 0
        if (shieldUp) spawnShieldParticles(level, player)

        if (!shieldUp) {
            syncClient()
            return // 护盾破碎：不弹开
        }

        // 检测并弹开进入护盾范围的敌方法术（自身法术不弹，避免出生即被弹回）。
        // 注：MagicManager 中的投射物均由 castHand/Trigger 设置 caster 后才加入，故可直接访问。
        val center = player.position().toVector3f()
        val aabb = player.getBoundingBox().inflate(shieldRadius)
        for (p in MagicManager.projectiles) {
            if (!p.alive) continue
            if (p.hitRadius <= 0.0) continue            // 不碰撞实体的法术（爆炸点等）不弹
            if (p.caster === player) continue
            val pos = Vector3f(p.status.pos)
            if (!aabb.inflate(p.hitRadius).contains(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())) continue

            // 弹开：以玩家中心为法线反射投射物速度；仅弹开正在靠近玩家的法术
            val normal = Vector3f(pos).sub(center)
            if (normal.lengthSquared() < 1e-4) continue
            normal.normalize()
            if (p.status.velocity.dot(normal) >= 0) continue
            val speed = p.status.velocity.length()
            if (speed < 1e-4) continue
            p.status.velocity.normalize().reflect(normal).mul(speed)
            p.status.pos = Vector3f(normal).mul(0.05f).add(pos)

            // 弹开命中粒子（密集爆闪，便于观察弹开效果）
            level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                14, 0.3, 0.3, 0.3, 0.12
            )
            level.sendParticles(
                ParticleTypes.END_ROD,
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                5, 0.15, 0.15, 0.15, 0.02
            )

            durability -= bounceCost
            if (durability <= 0) {
                durability = 0.0
                pauseTicks = breakPauseTicks
                break
            }
        }
        syncClient()
    }

    /** 护盾激活期间每 tick 在护盾表面密集生成粒子（便于观察护盾是否生效） */
    private fun spawnShieldParticles(level: ServerLevel, player: Entity) {
        val eye = player.getEyePosition()
        val r = 1.25
        repeat(6) {
            val theta = RANDOM.nextDouble() * Math.PI * 2
            val phi = (RANDOM.nextDouble() - 0.5) * Math.PI
            val x = eye.x + cos(theta) * cos(phi) * r
            val y = eye.y + sin(phi) * r * 0.8
            val z = eye.z + sin(theta) * cos(phi) * r
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.0, 0.0, 0.0, 0.02)
        }
    }

    /** 同步护盾状态到持有者客户端（仅状态变化时发包，避免每 tick 空包） */
    private fun syncClient() {
        val player = caster as? ServerPlayer ?: return
        val payload = SyncShieldPayload(wand.id, durability, maxDurability, pauseTicks, breakPauseTicks)
        if (payload == lastSent) return
        lastSent = payload
        PacketDistributor.sendToPlayer(player, payload)
    }

    private var lastSent = SyncShieldPayload("", -1.0, -1.0, -1, -1)

    companion object {
        private val RANDOM = java.util.Random()
    }

    override fun initLore() {
        lore(Lore.DESCRIPTION)
        lore(Lore.BR)
        lore(Lore.DURABILITY, Lore.formatDouble(maxDurability))
        lore(Lore.REGEN, Lore.formatDouble(regenPerSecond))
        lore(Lore.BREAK_PAUSE, breakPauseTicks)
        lore(Lore.BOUNCE_COST, Lore.formatDouble(bounceCost))
    }
}
