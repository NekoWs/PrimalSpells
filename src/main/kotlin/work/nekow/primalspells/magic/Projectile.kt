package work.nekow.primalspells.magic

import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import work.nekow.primalspells.magic.effect.Move
import work.nekow.primalspells.magic.effect.Position
import work.nekow.primalspells.magic.effect.Velocity
import work.nekow.primalspells.utils.HitResult
import work.nekow.primalspells.utils.HitType
import work.nekow.primalspells.utils.Lore
import work.nekow.primalspells.utils.LoreEntry

abstract class Projectile: Magic() {
    var status: MagicStatus = MagicStatus()

    override var cast: Int = 0

    var maxAge: Int = 0
    var alive: Boolean = true

    var maxBounces: Int = 0
    var maxHitTargets: Int = 1
    var bounceScaler: Vector3f = Vector3f(0.9f, 0.9f, 0.9f)

    lateinit var position: Vector3f
    lateinit var velocity: Vector3f
    var dragCoeff: Float = 0.02f
    var speed: Float = 1f

    override fun spell() {
        effects += Position(position)
        effects += Move(dragCoeff)
        effects += Velocity(velocity.mul(speed))

        effects.forEach {
            it.projectile = this
            it.status = status
        }

        super.spell()

        status.bounces = maxBounces
        status.hitTargets = 0
    }

    fun hitEntity(entity: Entity) {
        val impactNormal = Vector3f(status.pos).sub(entity.position().toVector3f()).normalize()
        val rebound = Vector3f(status.velocity).normalize().reflect(impactNormal)
        val result = HitResult(HitType.ENTITY, Vector3f(status.pos), rebound, entity)

        status.hitTargets++
        if (maxHitTargets > 0 && status.hitTargets >= maxHitTargets) {
            alive = false
        }

        effects.forEach { it.onHitEntity(entity) }
        effects.forEach { it.onHit(result) }
        onHitEntity(entity)
        onHit(result)
    }

    fun hitBlock(pos: Vector3f, faceNormal: Vector3f) {
        val rebound = Vector3f(status.velocity).normalize().reflect(faceNormal)
        val castPos = Vector3f(faceNormal).mul(0.01f).add(pos)
        val result = HitResult(HitType.BLOCK, castPos, rebound)

        if (status.bounces > 0) {
            status.bounces--
            val speed = status.velocity.length()
            status.velocity
                .normalize()
                .reflect(faceNormal)
                .mul(speed)
                .mul(bounceScaler.x, bounceScaler.y, bounceScaler.z)
            status.pos = castPos
        } else {
            alive = false
        }

        effects.forEach { it.onHitBlock(pos) }
        effects.forEach { it.onHit(result) }
        onHitBlock(pos)
        onHit(result)
    }

    open fun onHitEntity(entity: Entity) { }
    open fun onHitBlock(pos: Vector3f) { }
    open fun onHit(result: HitResult) { }

    override fun onSpell() { }

    override fun initLore() {
        super.initLore()
        if (status.damage > 0) lore += LoreEntry(Lore.DAMAGE, arrayOf(Lore.formatDouble(status.damage)))
    }

    override fun clone(): Projectile = super.clone() as Projectile
}
