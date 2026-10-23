package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3d
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.geometry.Bounce

/**
 * 弹跳修正（法术 id 为 `bounce`）—— 让投射物撞到方块时反弹而不是消失。
 *
 * 行为：
 * - 注入 [Bounce]`(8)`：把投射物的 `maxBounces` **增加 8**（是累加，不是设为 8；
 *   多个弹跳修正会继续累加）
 * - 撞击方块且弹跳成功（投射物仍存活）时，额外播放黏液块破碎音效作为弹性碰撞反馈
 * - 速度衰减由 `Projectile.bounceScaler`（默认 0.9）在反弹时统一处理，本类不修改它
 */
class MoreBounce : Modifier() {

    /** 修正的唯一标识符，与语言文件、模型、纹理文件名称对应 */
    override val id = "bounce"

    init {
        /** 施放该修正消耗的法力值 */
        mana = 2.0

        /** 该修正增加的施法延迟（单位：tick） */
        delay = 0

        effects += Bounce(8)

        // 添加弹跳效果：设置最大弹跳次数，并在每次弹跳时播放音效
        effects += object : BaseEffect() {

            /**
             * 投射物撞击方块时调用 —— 仅在弹跳成功时播放音效
             *
             * @param pos 碰撞方块坐标
             */
            override fun onHitBlock(pos: Vector3d) {
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
