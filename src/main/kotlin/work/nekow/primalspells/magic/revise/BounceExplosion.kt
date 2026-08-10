package work.nekow.primalspells.magic.revise

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.Explosion

/**
 * 弹跳爆炸修正 —— 投射物每次弹跳时触发一次爆炸。
 *
 * - 弹跳瞬间播放 TNT 引信音效
 * - 随后调用 [Explosion] 效果执行爆炸（使用 status 爆炸参数）
 */
class BounceExplosion : Revise() {

    override val id = "bounce_explosion"

    init {
        mana = 5.0
        delay = 1

        // 添加爆炸效果：在弹跳碰撞时触发
        effects += Explosion(onHit = true, onDeath = false)

        // 添加弹跳音效（与爆炸效果相互独立）
        effects += object : BaseEffect() {

            override fun onHitBlock(pos: Vector3f) {
                if (!projectile.alive) return

                val level = caster.level() as? ServerLevel ?: return

                level.playSound(
                    null,
                    pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                    SoundEvents.TNT_PRIMED,
                    SoundSource.HOSTILE,
                    0.7f,
                    1.2f
                )
            }
        }
    }
}
