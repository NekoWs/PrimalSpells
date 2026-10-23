package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*

/** 短命服务端冰冻状态；由 ProjectileRuntime 推进，退出世界时清空，不修改原有药水效果。 */
object FrozenStatus {
    private data class State(val target: LivingEntity, val level: ServerLevel, var until: Long, val shell: ParticleGroup)
    private val FREEZE_ID = Identifier.fromNamespaceAndPath("primalspells", "frozen_speed")
    private val states = HashMap<java.util.UUID, State>()
    private val immunity = HashMap<java.util.UUID, Pair<ServerLevel, Long>>()

    /** 目标在冻结窗口内才可被重击碎冰。 */
    fun isFrozen(target: LivingEntity): Boolean = states[target.uuid]?.let {
        it.target === target && it.level === target.level() && it.until > it.level.gameTime
    } ?: false

    /** 单次最多冻结 30 tick；解冻后 40 tick 免疫，防快速弹永久控制。 */
    fun freeze(target: LivingEntity, ticks: Int) {
        val level = target.level() as? ServerLevel ?: return
        if (immunity[target.uuid]?.let { it.first === level && it.second > level.gameTime } == true) return
        if (isFrozen(target)) return // 连续命中不刷新时长
        val center = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        val group = ParticleManager.of(level).createGroup(target.position())
        group.followEntity(target.uuid)
        Draw.sphere(ParticleManager.of(level), center, target.bbWidth.toDouble() * 0.7 + 0.15, 64,
            ColorSource.of(Color.ofInt(140, 226, 255, 255)), 0.6f, group = group,
            visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))
        group.scaleTo(0f, 0).scaleTo(1f, 2, work.nekow.particledrawing.core.easing.EasingType.EASE_OUT)
        val duration = ticks.coerceIn(1, 30)
        target.getAttribute(Attributes.MOVEMENT_SPEED)?.let {
            it.removeModifier(FREEZE_ID)
            it.addTransientModifier(AttributeModifier(FREEZE_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
        }
        states[target.uuid] = State(target, level, level.gameTime + duration, group)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(work.nekow.primalspells.integration.SpellEvents.StatusChanged(target, "primalspells:frozen", true, duration))
    }

    /** 碎冰只消耗一次冻结；碎片动画由 PD 编排。 */
    fun shatter(target: LivingEntity) { finish(target.uuid, true) }

    /** 在实体 tick 之前抑制水平运动和上跳；保留向下重力，玩家视角仍可操作。 */
    fun tick() {
        for ((id, state) in states.toMap()) {
            if (!state.target.isAlive || state.target.level() !== state.level || state.level.gameTime >= state.until) {
                finish(id, false)
            } else {
                val v = state.target.deltaMovement
                state.target.deltaMovement = Vec3(0.0, v.y.coerceAtMost(0.0), 0.0)
                state.target.hurtMarked = true
            }
        }
        immunity.entries.removeIf { it.value.first.gameTime >= it.value.second }
    }

    private fun finish(id: java.util.UUID, shattered: Boolean) {
        val state = states.remove(id) ?: return
        immunity[id] = state.level to (state.level.gameTime + 40)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(work.nekow.primalspells.integration.SpellEvents.StatusChanged(state.target, "primalspells:frozen", false, 0))
        state.target.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(FREEZE_ID)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.shatter(state.shell, 5)
        work.nekow.primalspells.magic.effect.visual.PixelVfx.burst(ParticleManager.of(state.level), state.target.boundingBox.center,
            Color.ofInt(128, 222, 255), if (shattered) 192 else 64, if (shattered) 0.35 else 0.1, 12)
    }

    /** 服务器停止时不留下实体引用或粒子组。 */
    fun clear() { states.keys.toList().forEach { finish(it, false) }; states.clear(); immunity.clear() }
}
