package work.nekow.primalspells.magic.revise

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.Explosion
import work.nekow.primalspells.utils.HitResult
import work.nekow.primalspells.utils.HitType
import kotlin.random.Random

/**
 * 集束炸弹修正 —— 在投射物弹跳或命中时发射 3 枚爆炸子弹。
 *
 * 爆炸子弹特性：
 * - 受重力影响
 * - 发射速度 = 投射物当前速度 × 0.8
 * - 发射方向 = 投射物方向 + 0.2 倍随机散射
 * - 命中实体/方块时触发爆炸
 * - 继承投射物的爆炸参数
 */
class ClusterBomb : Revise() {

    override val id = "cluster_bomb"

    /** 散射强度 */
    private val scatterStrength = 0.2f

    /** 速度比例 */
    private val speedRatio = 0.8f

    /** 子弹数量 */
    private val bulletCount = 3

    init {
        mana = 6.0
        delay = 2

        effects += object : BaseEffect() {

            /**
             * 投射物碰撞时生成爆炸子弹
             *
             * @param result 碰撞结果
             */
            override fun onHit(result: HitResult) {
                if (!projectile.alive && result.type != HitType.ENTITY) return

                val level = caster.level() as? ServerLevel ?: return

                // 克隆基础投射物（不附带修正）
                val currentDir = Vector3f(status.velocity).normalize()
                val currentSpeed = status.velocity.length()

                for (i in 0 until bulletCount) {
                    val bullet = projectile.clone()

                    // 随机散射方向
                    val scatter = Vector3f(
                        Random.nextFloat() - 0.5f,
                        Random.nextFloat() - 0.5f,
                        Random.nextFloat() - 0.5f
                    ).normalize()

                    // 发射方向 = 投射物方向 + 散射偏移
                    val launchDir = Vector3f(currentDir)
                        .add(scatter.mul(scatterStrength))
                        .normalize()

                    // 发射速度 = 投射物当前速度 × 比例
                    val launchVel = Vector3f(launchDir).mul(currentSpeed * speedRatio)

                    bullet.caster = caster
                    bullet.wand = wand
                    bullet.position = Vector3f(result.pos)
                    bullet.velocity = Vector3f(launchVel)

                    // 子弹受重力影响
                    bullet.dragCoeff = 0.0f
                    bullet.throughBlocks = false

                    // 设置爆炸参数（继承当前状态参数）
                    // 挂载爆炸效果：命中实体/方块时触发
                    bullet.effects += Explosion(onHit = true, onDeath = false)

                    // 额外挂载重力修正的效果（直接操作 onTick）
                    bullet.effects += object : BaseEffect() {
                        override fun onTick() {
                            if (!projectile.alive) return
                            status.velocity.y -= 0.03f
                        }
                    }

                    bullet.spell()
                    MagicManager.add(bullet)
                }

                // 播放散开音效
                level.playSound(
                    null,
                    result.pos.x.toDouble(), result.pos.y.toDouble(), result.pos.z.toDouble(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.NEUTRAL,
                    0.5f,
                    1.8f
                )
            }
        }
    }
}
