package work.nekow.primalspells.magic.projectile

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.ConcentratedLightFx
import work.nekow.primalspells.magic.effect.visual.WandCastingFx
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.wand.WandAim

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
        maxBounces = 1; bounceScaler.set(1f,1f,1f)
        recoil = 0.7; mass = 2.0; status.damage = 36.0
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
            if (castFromWand) {
                val origin = WandCastingFx.origin(caster, wand)
                status.pos.set(origin.x.toFloat(),origin.y.toFloat(),origin.z.toFloat())
            }
            if (status.age < CHARGE_TICKS) { visuals.onTick(); return }
            val direction = if (castFromWand) WandAim.direction(caster,Vec3(status.pos)) else launchDirection
            status.velocity.set(direction.x.toFloat(),direction.y.toFloat(),direction.z.toFloat()).mul(launchSpeed)
            awaitingRelease = false; motionEnabled = true; releasedAtAge = status.age
            if (castFromWand) WandCastingFx.cast(caster, wand)
            releaseCastRecoil()
        }
        super.tick()
    }

    override fun initLore() {
        super.initLore()
        lore(Lore.CHARGE_TIME, CHARGE_TICKS)
    }

    companion object { const val CHARGE_TICKS = 20 }
}
