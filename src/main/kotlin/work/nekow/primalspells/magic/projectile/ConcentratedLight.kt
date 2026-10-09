package work.nekow.primalspells.magic.projectile

import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.ConcentratedLightFx
import work.nekow.primalspells.util.Lore

/** 蓄能时只更新光学棱片；释放后所有修正与碰撞仍走原投射物流水线。 */
class ConcentratedLight : Projectile() {
    override val id = "concentrated_light"
    override val bodyColor = Color.ofInt(255, 225, 128)
    private val visuals = ConcentratedLightFx()
    private var launchSpeed = 0f
    private var world: net.minecraft.world.level.Level? = null
    var releasedAtAge: Int? = null
        private set

    init {
        mana = 90.0; delay = 70; recharge = 20; maxAge = 80
        speed = 3f; dragCoeff = 0f; hitRadius = 0.15
        maxBounces = 5; bounceScaler.set(1f,1f,1f)
        recoil = 0.06; recoilOnCast = true; mass = 2.0; status.damage = 36.0
        damageType = SpellDamageType.ENERGY
        awaitingRelease = true
        effects += Hurt()
        effects += visuals
    }

    override fun onSpell() {
        world = caster.level()
        launchSpeed = status.velocity.length()
        status.velocity.zero()
        motionEnabled = false
    }

    override fun tick() {
        if (awaitingRelease) {
            if (!caster.isAlive || caster.level() !== world) { alive = false; return }
            if (status.age < CHARGE_TICKS) { visuals.onTick(); return }
            // 出手即锁定世界坐标与方向；玩家走动/转身不再操纵已部署的聚光阵。
            val direction = launchDirection
            status.velocity.set(direction.x.toFloat(),direction.y.toFloat(),direction.z.toFloat()).mul(launchSpeed)
            awaitingRelease = false; motionEnabled = true; releasedAtAge = status.age
            // 出手已轻推一次；释放只播放镜阵反冲，不再施加第二次推力。
        }
        super.tick()
    }

    override fun initLore() {
        super.initLore()
        lore(Lore.CHARGE_TIME, CHARGE_TICKS)
    }

    companion object { const val CHARGE_TICKS = 20 }
}
