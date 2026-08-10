package work.nekow.primalspells.magic.revise

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import org.joml.Vector3f
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.Bounce

/**
 * 弹跳爆炸修正法术
 *
 * 为投射物附加爆炸效果：每次投射物与方块碰撞并发生弹跳时，
 * 在碰撞位置触发一次原版爆炸，并在爆炸前播放引信音效。
 *
 * 工作原理：
 * - 该修正向投射物注入一个自定义效果（匿名 BaseEffect 子类）
 * - 效果重写 [BaseEffect.onHitBlock]，该回调在投射物撞击方块时被调用
 * - 通过检测 [projectile.alive] 判断投射物是否真正发生了弹跳
 *   （若弹跳次数耗尽，投射物会在同帧内被标记为死亡，alive = false）
 * - 仅在弹跳发生时播放 TNT 引信音效，然后调用 [ServerLevel.explode] 生成原版爆炸
 *
 * 爆炸参数：
 * - 爆炸威力：2.0（不会破坏方块）
 * - 爆炸类型：[Level.ExplosionInteraction.NONE]（无额外交互）
 * - 爆炸源：[caster]（施法者作为爆炸来源实体）
 *
 * 音效：
 * - 弹跳瞬间播放 TNT 引信声（[SoundEvents.TNT_PRIMED]）
 * - [ServerLevel.explode] 自动播放原版爆炸音效
 */
class BounceExplosion : Revise() {

    /** 修正的唯一标识符，与语言文件、模型、纹理文件名称对应 */
    override val id = "bounce_explosion"

    init {
        /** 施放该修正消耗的法力值 */
        mana = 5.0

        /** 该修正增加的施法延迟（单位：tick） */
        delay = 1

        effects += Bounce()

        // 向修正的效果列表中添加一个匿名效果实例
        // 该效果会在投射物弹跳时播放音效并触发原版爆炸
        effects += object : BaseEffect() {

            /**
             * 当投射物撞击方块时调用
             *
             * 判断逻辑：
             * - 若 [projectile.alive] 为 false，说明投射物因弹跳次数耗尽而死亡，
             *   并非真正的弹跳，此时不触发爆炸，直接返回
             * - 若 [projectile.alive] 为 true，说明投射物成功弹跳：
             *   1. 先播放 TNT 引信音效作为爆炸预兆
             *   2. 再在碰撞位置生成原版爆炸（爆炸自带爆炸音效）
             *
             * @param pos 碰撞位置的方块坐标（Vector3f）
             */
            override fun onHitBlock(pos: Vector3f) {
                // 仅在实际发生弹跳时触发爆炸和音效
                if (!projectile.alive) return

                // 获取施法者所在世界的服务端实例，客户端不执行
                val level = caster.level() as? ServerLevel ?: return

                // 播放 TNT 引信音效 —— 弹跳瞬间的爆炸预兆声
                level.playSound(
                    null,                      // 播放给所有附近玩家
                    pos.x.toDouble(),
                    pos.y.toDouble(),
                    pos.z.toDouble(),
                    SoundEvents.TNT_PRIMED,    // TNT 引信音效
                    SoundSource.HOSTILE,       // 敌对音源（爆炸类）
                    0.7f,                      // 音量
                    1.2f                       // 略高音调
                )

                // 在碰撞位置触发原版爆炸
                // explode 参数说明：
                //   source        — 爆炸来源实体（施法者）
                //   damageSource  — 伤害来源（null 使用默认）
                //   context       — 爆炸上下文（null 使用默认）
                //   x, y, z       — 爆炸中心坐标
                //   radius        — 爆炸威力 / 半径
                //   causesFire    — 是否引起火焰（false = 不生成火焰）
                //   mode          — 爆炸交互模式（NONE = 不破坏方块）
                level.explode(
                    caster,                            // 爆炸来源：施法者
                    null,                              // 伤害来源：默认
                    null,                              // 爆炸上下文：默认
                    pos.x.toDouble(),                  // 爆炸中心 X
                    pos.y.toDouble(),                  // 爆炸中心 Y
                    pos.z.toDouble(),                  // 爆炸中心 Z
                    2.0f,                              // 爆炸半径
                    false,                             // 不产生火焰
                    Level.ExplosionInteraction.NONE    // 不破坏方块
                )
            }
        }
    }
}
