package work.nekow.primalspells.entity

import net.minecraft.world.entity.ai.goal.Goal
import java.util.EnumSet
import kotlin.math.atan2
import kotlin.math.sqrt

/** 可读的20刻准备、短点射与恢复；失去视线或受到攻击时取消准备，不隔墙施法。 */
class RogueMageAttackGoal(private val mage: RogueMageEntity) : Goal() {
    private var windup = 0
    private var recovery = 0
    private var burst = 0
    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK) }
    override fun canUse() = mage.target?.isAlive == true
    override fun requiresUpdateEveryTick() = true
    override fun stop() { mage.cancelCast(); windup = 0; burst = 0; mage.navigation.stop() }

    override fun tick() {
        val target = mage.target ?: return
        val distance = mage.distanceToSqr(target)
        val visible = mage.sensing.hasLineOfSight(target)
        mage.lookControl.setLookAt(target, 30f, 30f)
        val delta = target.boundingBox.center.subtract(mage.eyePosition)
        mage.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        mage.xRot = -Math.toDegrees(atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        mage.yHeadRot = mage.yRot
        if (distance > 14 * 14 || !visible) mage.navigation.moveTo(target, 0.9) else mage.navigation.stop()
        if (recovery > 0) { recovery--; return }
        if (!visible || distance > 18 * 18) { mage.cancelCast(); windup = 0; burst = 0; return }
        if (windup > 0) {
            if (!mage.charging) { windup = 0; burst = 0; recovery = 20; return }
            windup--
            if (windup == 0) {
                mage.releaseCast()
                burst = 2
                recovery = 12
            }
        } else if (burst > 0) {
            if (mage.releaseCast()) burst--
            recovery = if (burst > 0) 12 else 45
        } else {
            mage.prepareCast()
            windup = 20
        }
    }
}
