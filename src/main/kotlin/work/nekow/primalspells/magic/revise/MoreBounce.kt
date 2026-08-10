package work.nekow.primalspells.magic.revise

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.Bounce

/**
 * 弹跳修正 —— 使投射物与方块碰撞时可以反弹，而非直接消失
 *
 * 效果：
 * - 将投射物的 [maxBounces] 设置为 10，允许 10 次弹跳
 * - 弹跳时速度会按 [bounceScaler] 衰减
 * - 每次弹跳碰撞时播放史莱姆弹跳音效
 *
 * 音效：
 * - 弹跳时播放黏液块破坏声（[SoundEvents.SLIME_BLOCK_BREAK]），
 *   模拟弹性碰撞的声效
 */
class MoreBounce : Revise() {

    /** 修正的唯一标识符，与语言文件、模型、纹理文件名称对应 */
    override val id = "bounce"

    init {
        /** 施放该修正消耗的法力值 */
        mana = 2.0

        /** 该修正增加的施法延迟（单位：tick） */
        delay = 1

        effects += Bounce(8)

        // 添加弹跳效果：设置最大弹跳次数，并在每次弹跳时播放音效
        effects += object : BaseEffect() {

            /**
             * 投射物撞击方块时调用 —— 仅在弹跳成功时播放音效
             *
             * @param pos 碰撞方块坐标
             */
            override fun onHitBlock(pos: Vector3f) {
                // 仅当投射物存活（即弹跳成功, 而非死亡）时播放音效
                if (!projectile.alive) return

                // 获取服务端世界实例
                val level = caster.level() as? ServerLevel ?: return

                // 播放史莱姆弹跳音效
                level.playSound(
                    null,                              // 播放给所有附近玩家
                    pos.x.toDouble(),
                    pos.y.toDouble(),
                    pos.z.toDouble(),
                    SoundEvents.SLIME_BLOCK_BREAK,     // 黏液块破坏声（弹跳感）
                    SoundSource.NEUTRAL,               // 环境音源
                    0.5f,                              // 较低音量
                    1.3f                               // 略高音调
                )
            }
        }
    }
}
