package work.nekow.primalspells.magic.modifier

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.Lore

/**
 * 量子纠缠修正 —— 将下一个投射物复制两份。
 *
 * - 两份复制体继承主投射物的全部基础属性（伤害、速度、粒子轨迹等）
 * - 复制体环绕主投射物做正弦（sin）运动（圆周 + 竖直正弦起伏）
 * - 主投射物与两份复制体互为纠缠：任意一个死亡，其余全部死亡
 */
class QuantumEntanglement : Modifier() {

    override val id = "quantum_entanglement"

    /** 环绕半径 */
    private val orbitRadius = 2.2f

    /** 环绕角速度（弧度/tick） */
    private val angularSpeed = 0.3f

    /** 竖直正弦起伏幅度（相对半径的比例） */
    private val verticalFactor = 0.4f

    init {
        mana = 6.0
        delay = 0

        effects += object : BaseEffect() {
            override fun onActive() {
                val level = caster.level() as? ServerLevel ?: return

                val link = EntanglementLink()
                link.members += projectile

                for (i in 0 until 2) {
                    val clone = work.nekow.primalspells.magic.core.SpellAssembly.copy(projectile)
                    clone.caster = caster
                    clone.wand = wand
                    clone.position = Vector3d(projectile.status.pos)
                    clone.velocity = Vector3f(projectile.velocity)
                    clone.effects += OrbitEffect(
                        leader = projectile,
                        phaseOffset = i * PI.toFloat(),
                        radius = orbitRadius,
                        angularSpeed = angularSpeed,
                        verticalFactor = verticalFactor,
                        link = link,
                    )
                    link.members += clone
                    clone.spell()
                    ProjectileRuntime.add(clone)
                }

                // 主投射物也需要监听复制体的死亡
                projectile.effects += LinkEffect(link)
            }
        }

        lore(Lore.DESCRIPTION)
    }

    /** 纠缠关联组 —— 共享成员列表，任一成员死亡则全部死亡 */
    private class EntanglementLink {
        val members = arrayListOf<Projectile>()

        fun propagateDeath() {
            if (members.any { !it.alive }) {
                members.forEach { it.alive = false }
            }
        }
    }

    /** 主投射物的死亡传播效果（监听复制体是否死亡） */
    private class LinkEffect(private val link: EntanglementLink) : BaseEffect() {
        override fun onTick() {
            link.propagateDeath()
        }
    }

    /** 环绕效果 —— 复制体环绕主投射物做正弦运动，并同步死亡状态 */
    private class OrbitEffect(
        private val leader: Projectile,
        private val phaseOffset: Float,
        private val radius: Float,
        private val angularSpeed: Float,
        private val verticalFactor: Float,
        private val link: EntanglementLink,
    ) : BaseEffect() {
        /** 给Move真实轨道位移，复制体沿轨道扫掠并正常命中。 */
        override val phase = EffectPhase.GUIDANCE

        private var orbitPhase = 0f

        override fun onActive() {
            orbitPhase = phaseOffset
            status.pos.add((radius * cos(orbitPhase)).toDouble(), 0.0, (radius * sin(orbitPhase)).toDouble())
        }

        override fun onTick() {
            if (!projectile.alive) return

            // 任一成员死亡 → 全部死亡
            link.propagateDeath()
            if (!projectile.alive) return

            // 以主投射物当前位置为圆心
            val cx = leader.status.pos.x
            val cy = leader.status.pos.y
            val cz = leader.status.pos.z

            orbitPhase += angularSpeed
            val dx = radius * cos(orbitPhase)
            val dy = radius * verticalFactor * sin(orbitPhase * 2f)
            val dz = radius * sin(orbitPhase)

            status.velocity.set((cx + dx - status.pos.x).toFloat(), (cy + dy - status.pos.y).toFloat(), (cz + dz - status.pos.z).toFloat())
        }
    }
}
