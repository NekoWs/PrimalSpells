package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.lifecycle.Explosion
import kotlin.random.Random

/**
 * 爆炸投射修正 —— 每隔 10 tick 向垂直方向随机发射一枚爆炸火球。
 *
 * 火球特性：
 * - 发射方向：垂直于投射物行进方向的随机方向
 * - 初始速度：1.0
 * - 命中时触发爆炸
 * - 继承投射物的爆炸参数
 */
class ExplosionProjectile : Modifier() {

    override val id = "explosion_projectile"

    /** 发射间隔（tick） */
    private val interval = 10

    /** 火球初始速度 */
    private val bulletSpeed = 1.0f

    init {
        mana = 5.0
        delay = 0

        effects += object : BaseEffect() {

            /**
             * 每隔 [interval] tick 发射一枚爆炸火球
             */
            override fun onTick() {
                if (!projectile.alive) return
                if (status.age <= 0 || status.age % interval != 0) return

                val level = caster.level() as? ServerLevel ?: return

                // 当前行进方向
                val forward = if (status.velocity.lengthSquared() > 1e-8f) Vector3f(status.velocity).normalize()
                    else Vector3f(projectile.launchDirection.x.toFloat(), projectile.launchDirection.y.toFloat(), projectile.launchDirection.z.toFloat())

                // 生成垂直于行进方向的随机方向
                val perp = genRandomPerpendicular(forward)

                // 重建投射物并继承非生成型修饰配方
                val bullet = projectile.clone().also { work.nekow.primalspells.magic.core.SpellAssembly.inherit(projectile, it) }
                bullet.caster = caster
                bullet.wand = wand
                bullet.position = Vector3f(status.pos)
                bullet.velocity = work.nekow.primalspells.magic.core.SpellAssembly.launch(bullet, perp, bulletSpeed)

                // 设置爆炸效果
                bullet.effects.removeAll { it is Explosion }
                bullet.effects += Explosion(onHit = true, onDeath = false)

                bullet.spell()
                MagicManager.add(bullet)

                // 播放发射音效
                level.playSound(
                    null,
                    status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble(),
                    SoundEvents.FIREWORK_ROCKET_SHOOT,
                    SoundSource.NEUTRAL,
                    0.4f,
                    1.5f
                )
            }

            /**
             * 生成与输入向量正交的随机单位向量
             */
            private fun genRandomPerpendicular(v: Vector3f): Vector3f {
                // 选择一个不与 v 平行的任意向量
                val arbitrary = if (absF(v.x) > 0.9f) {
                    Vector3f(0f, 1f, 0f)
                } else {
                    Vector3f(1f, 0f, 0f)
                }
                // 第一次叉乘得到垂直向量
                val perpendicular = Vector3f(v).cross(arbitrary).normalize()
                // 随机旋转角度
                val angle = Random.nextFloat() * 2.0f * Math.PI.toFloat()
                // angle 是 Float，kotlin.math.cos/sin 对 Float 直接返回 Float，无需再 toFloat()
                val cosA = kotlin.math.cos(angle)
                val sinA = kotlin.math.sin(angle)
                // 绕 v 旋转 perpendicular
                return Vector3f(perpendicular).mul(cosA)
                    .add(Vector3f(perpendicular).cross(v).normalize().mul(sinA))
                    .normalize()
            }

            private fun absF(x: Float): Float = if (x < 0f) -x else x
        }
    }
}
