package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.geometry.ConstantAcceleration
import work.nekow.primalspells.magic.effect.lifecycle.Explosion
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.util.HitType
import kotlin.random.Random

/**
 * 集束炸弹修正 —— 在投射物弹跳或命中时发射 3 枚爆炸子弹。
 *
 * 爆炸子弹特性：
 * - 受重力影响（挂载共用的 [ConstantAcceleration]）
 * - 发射速度 = 投射物当前速度 × 0.8
 * - 发射方向 = 投射物方向 + 0.2 倍随机散射
 * - 命中实体/方块时触发爆炸（[Explosion]，爆炸参数为 MagicStatus 默认值——子弹是全新实例，不继承父投射物状态）
 */
class ClusterBomb : Modifier() {

    override val id = "cluster_bomb"

    /** 散射强度 */
    private val scatterStrength = 0.2f

    /** 速度比例 */
    private val speedRatio = 0.8f

    /** 子弹数量 */
    private val bulletCount = 3

    init {
        mana = 6.0
        delay = 0

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

                    // 子弹的物理参数：无阻尼、撞墙即炸（不再穿墙）
                    bullet.dragCoeff = 0.0f
                    bullet.throughBlocks = false

                    // 注意 projectile.clone() 是「从注册表新建实例」，不会继承父投射物的运行期状态，
                    // 因此爆炸参数是 MagicStatus 的默认值（半径 2 / 伤害 5），需要什么效果就在这里显式装配。
                    bullet.effects += Explosion(onHit = true, onDeath = false)
                    bullet.effects += ConstantAcceleration()

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
