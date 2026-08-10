package work.nekow.primalspells.magic.revise

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.Bounce
import work.nekow.primalspells.utils.HitResult
import work.nekow.primalspells.utils.HitType
import kotlin.random.Random

/**
 * 弹跳复制修正 —— 在投射物每次弹跳时生成一个自身复制体
 *
 * 复制体特性：
 * - 仅包含基础投射物（不附带任何修正效果）
 * - 发射方向 = 弹跳方向 + [randomStrength] 倍的随机偏移向量
 * - 速度大小 = 弹跳时投射物的当前速度
 * - 复制体立即施放并加入 MagicManager 管理
 *
 * 音效：
 * - 复制体生成时播放紫水晶钟鸣声
 */
class BounceDuplicate : Revise() {

    /** 修正的唯一标识符，与语言文件、模型、纹理文件名称对应 */
    override val id = "bounce_duplicate"

    /** 随机偏移强度（0.5 = 弹跳方向 ± 50% 随机偏移） */
    private val randomStrength = 0.5f

    init {
        /** 施放该修正消耗的法力值 */
        mana = 6.0

        /** 该修正增加的施法延迟（单位：tick） */
        delay = 2

        effects += Bounce()

        // 向修正效果列表中添加弹跳复制匿名效果
        effects += object : BaseEffect() {
            /**
             * 投射物发生碰撞（击中实体或方块）后调用
             *
             * 仅在投射物弹跳成功（[projectile.alive] 为 true）
             * 且碰撞类型为方块时才生成复制体。
             *
             * @param result 碰撞结果，包含碰撞类型、位置、反弹方向法线
             */
            override fun onHit(result: HitResult) {
                // 仅方块碰撞 + 弹跳成功时生成复制体
                if (result.type != HitType.BLOCK) return
                if (!projectile.alive) return

                // 获取服务端世界实例
                val level = caster.level() as? ServerLevel ?: return

                // 克隆基础投射物 —— 不附带任何修正效果
                val clone = projectile.clone()

                // 设置复制体的施法者和法杖（继承原投射物）
                clone.caster = caster
                clone.wand = wand

                // 生成单位球面上的随机方向偏移向量
                val randomOffset = Vector3f(
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f,
                    Random.nextFloat() - 0.5f
                ).normalize()

                // 计算复制体的发射方向：
                // 弹跳方向（result.normal）+ randomStrength * 随机偏移 → 归一化
                val launchDirection = Vector3f(result.normal)
                    .add(randomOffset.mul(randomStrength))
                    .normalize()

                // 复制体速度 = 发射方向 × 原投射物当前速度大小
                val speedMagnitude = status.velocity.length()
                val launchVelocity = Vector3f(launchDirection).mul(speedMagnitude)

                // 设置复制体的初始位置（弹跳碰撞点）和速度
                clone.position = Vector3f(result.pos)
                clone.velocity = Vector3f(launchVelocity)

                // 施放复制体并注册到管理器
                clone.spell()
                MagicManager.add(clone)

                // 播放紫水晶钟鸣声 —— 复制体生成提示音
                level.playSound(
                    null,                                // 播放给所有附近玩家
                    result.pos.x.toDouble(),
                    result.pos.y.toDouble(),
                    result.pos.z.toDouble(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,    // 紫水晶钟鸣
                    SoundSource.NEUTRAL,                 // 环境音源
                    0.6f,                                // 音量
                    1.4f                                 // 音调
                )
            }
        }
    }
}
