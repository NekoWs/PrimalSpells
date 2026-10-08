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
import kotlin.random.Random

/**
 * 集束炸弹修正 —— 在投射物弹跳或命中时发射 3 枚爆炸子弹。
 *
 * 爆炸子弹特性：
 * - 受重力影响（挂载共用的 [ConstantAcceleration]）
 * - 发射速度 = 投射物当前速度 × 0.8
 * - 发射方向 = 投射物方向 + 0.2 倍随机散射
 * - 命中实体/方块时触发爆炸（[Explosion]，爆炸参数为 MagicStatus 默认值——子弹重建非生成型修饰配方）
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

                val level = caster.level() as? ServerLevel ?: return

                // 重建投射物并继承非生成型修饰配方
                val currentDir = if (status.velocity.lengthSquared() > 1e-8f) Vector3f(status.velocity).normalize()
                    else Vector3f(projectile.launchDirection.x.toFloat(), projectile.launchDirection.y.toFloat(), projectile.launchDirection.z.toFloat())
                val currentSpeed = status.velocity.length().coerceAtLeast(0.4f)

                for (i in 0 until bulletCount) {
                    val bullet = projectile.clone().also { work.nekow.primalspells.magic.core.SpellAssembly.inherit(projectile, it) }

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

                    bullet.caster = caster
                    bullet.wand = wand
                    bullet.position = Vector3f(result.pos)
                    bullet.velocity = work.nekow.primalspells.magic.core.SpellAssembly.launch(bullet, launchDir, currentSpeed * speedRatio)

                    // 子弹的物理参数：无阻尼、撞墙即炸（不再穿墙）
                    bullet.dragCoeff = 0.0f
                    bullet.throughBlocks = false

                    // 数值由子弹基础状态按父配方重放，运动速度通过launch抵消重复倍率。
                    bullet.effects.removeAll { it is Explosion }
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
