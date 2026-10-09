package work.nekow.primalspells.magic.modifier

import org.joml.Quaternionf
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 以方向精度换取连续施法：全空间散射，不改变当前弹速。 */
class OmnidirectionalScatter : Modifier() {
    override val id = "omnidirectional_scatter"
    init {
        mana = 2.0
        delay = -10
        recharge = -20
        effects += object : BaseEffect() {
            override val phase = EffectPhase.GUIDANCE
            override fun onActive() {
                if (!isStackPrimary || status.velocity.lengthSquared() < 1e-8f) return
                val random = caster.level().random
                val y = random.nextFloat() * 2f - 1f
                val angle = random.nextFloat() * (Math.PI * 2).toFloat()
                val radius = sqrt(1f - y * y)
                val aim = Vector3f(radius * cos(angle), y, radius * sin(angle))
                Quaternionf().rotationTo(Vector3f(status.velocity).normalize(), aim).transform(status.velocity)
            }
        }
    }
}
