package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3d
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.lifecycle.Explosion

/**
 * 弹跳爆炸修正 —— 投射物每次弹跳时触发一次爆炸。
 *
 * - 弹跳瞬间播放 TNT 引信音效
 * - 随后调用 [Explosion] 效果执行爆炸（使用 status 爆炸参数）
 */
class BounceExplosion : Modifier() {

    override val id = "bounce_explosion"

    init {
        mana = 5.0
        delay = 0

        effects += work.nekow.primalspells.magic.effect.geometry.Bounce()

        // 添加爆炸效果：在弹跳碰撞时触发
        effects += Explosion(onHit = true, onDeath = false, bounceOnly = true)

        // 添加弹跳音效（与爆炸效果相互独立）
        effects += object : BaseEffect() {

            override fun onHitBlock(pos: Vector3d) {
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
