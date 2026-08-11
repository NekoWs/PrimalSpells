package work.nekow.primalspells.magic.projectile

import net.minecraft.core.particles.ParticleTypes
import org.joml.Vector3f
import work.nekow.primalspells.magic.Magic
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.TriggerSpell
import work.nekow.primalspells.magic.effect.DelayedTrigger
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.utils.Lore

/**
 * 延迟施法投射物 —— 存活 20 tick 后死亡，
 * 死亡时以施法朝向释放 payload 中的法术。
 */
class DelayedCastProjectile : Projectile(), TriggerSpell {

    override val id = "delayed_cast"

    override var triggerCast = 1
    override var payload = arrayListOf<Magic>()

    /** 施法朝向（单位向量），在 spell() 被 mul(speed=0) 归零前保存 */
    var spellDirection: Vector3f = Vector3f()

    init {
        mana = 3.0
        delay = 1
        recharge = 1
        maxAge = 20
        speed = 0f
        hitRadius = 0.0
        effects += Trajectory(ParticleTypes.HAPPY_VILLAGER)
        effects += DelayedTrigger(this) // 传入自身引用以读取 spellDirection
    }

    override fun spell() {
        // 在 Velocity(velocity.mul(speed=0)) 归零 velocity 之前保存朝向
        spellDirection = Vector3f(velocity)
        super.spell()
    }

    override fun initLore() {
        super.initLore()
        lore(Lore.DESCRIPTION)
    }
}
