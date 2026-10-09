package work.nekow.primalspells.entity

import net.minecraft.world.entity.ai.goal.Goal
import java.util.EnumSet
import kotlin.math.atan2
import kotlin.math.sqrt

/** 按流派准备、短点射与恢复；失去视线或受到攻击时取消准备，不隔墙施法。 */
class RogueMageAttackGoal(private val mage: RogueMageEntity) : Goal() {
    private var windup = 0
    private var recovery = 0
    private var burst = 0
    private var tacticalAt = 0
    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK) }
    override fun canUse() = mage.target?.isAlive == true
    override fun requiresUpdateEveryTick() = true
    override fun stop() { mage.cancelCast(); windup = 0; burst = 0; recovery = 0; mage.navigation.stop() }

    override fun tick() {
        val target = mage.target ?: return
        val distance = mage.distanceToSqr(target)
        val visible = mage.sensing.hasLineOfSight(target)
        mage.lookControl.setLookAt(target, 30f, 30f)
        val delta = target.boundingBox.center.subtract(mage.eyePosition)
        mage.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        mage.xRot = -Math.toDegrees(atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        mage.yHeadRot = mage.yRot
        val school = mage.school
        if (recovery > 0) {
            recovery--
            if (mage.tickCount >= tacticalAt) {
                tacticalAt = mage.tickCount + 12
                val point = MageTactics.destination(mage, target, cover = burst == 0 && recovery > 12)
                if (point != null) mage.navigation.moveTo(point.x, point.y, point.z, 1.0)
                else if (distance < 25) MageTactics.destination(mage, target, false)?.let { mage.navigation.moveTo(it.x, it.y, it.z, 1.0) }
            }
            return
        }
        if (distance < 25 && windup == 0) MageTactics.destination(mage, target, false)?.let { mage.navigation.moveTo(it.x, it.y, it.z, 1.0) }
        else if (distance > school.range * school.range || !visible) mage.navigation.moveTo(target, 0.9)
        else mage.navigation.stop()
        if (!visible || distance > 18 * 18) { mage.cancelCast(); windup = 0; burst = 0; return }
        if (windup > 0) {
            if (!mage.charging) { windup = 0; burst = 0; recovery = 20; return }
            windup--
            if (windup == 0) {
                mage.releaseCast()
                burst = school.burst - 1 + if (mage.elite) 1 else 0
                recovery = if (burst > 0) 12 else school.recovery
            }
        } else if (burst > 0) {
            if (mage.releaseCast()) burst--
            recovery = if (burst > 0) 12 else school.recovery
        } else {
            mage.prepareCast()
            windup = school.windup
        }
    }
}
