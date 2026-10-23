package work.nekow.primalspells.magic.runtime

import work.nekow.primalspells.magic.effect.lifecycle.FrozenStatus
import work.nekow.primalspells.magic.effect.lifecycle.SpellInteractions
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.Deferred

/** 服务端临时弹体生命周期；新增对象在遍历后统一入列，停服不保留任何待加入弹体。 */
object ProjectileRuntime {
    val projectiles = arrayListOf<Projectile>()
    private val pendingAdd = arrayListOf<Projectile>()

    /** 只排队，不调用spell；调用方激活后，在tick遍历结束统一入列。 */
    fun add(projectile: Projectile) {
        pendingAdd.add(projectile)
    }

    /** 仅由服务端时钟推进；到期当刻通知expire，死亡清理延至下一次遍历。 */
    fun tick() {
        FrozenStatus.tick()
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val projectile = iter.next()
            if (!projectile.alive) {
                projectile.die()
                iter.remove()
                continue
            }
            projectile.tick()
            projectile.status.age++
            if (projectile.status.age >= projectile.maxAge && projectile.alive) {
                projectile.expire()
                projectile.alive = false
            }
        }
        projectiles.addAll(pendingAdd)
        pendingAdd.clear()
        SpellInteractions.tick(projectiles.toList())
        // 延迟重音（塌缩爆闪、波环落地溅射…）也在这里推进：它们必须在效果死掉之后仍然到点触发
        Deferred.tick()
    }

    /** 停服清理也覆盖待加入队列，避免集成服务器重开世界继承上一世界的弹体。 */
    fun clear() {
        projectiles.clear()
        pendingAdd.clear()
        FrozenStatus.clear()
        Deferred.clear()
    }
}
