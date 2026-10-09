package work.nekow.primalspells.entity

import net.minecraft.core.BlockPos
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.RandomSource
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.loot.SpellLoot
import work.nekow.primalspells.loot.SpellLootProfile

/** 遗迹中的敌对持杖者；使用玩家同一套抽牌/法力/冷却，死亡掉落原手持法杖。 */
class RogueMageEntity(type: EntityType<out RogueMageEntity>, level: Level) : Monster(type, level) {
    val charging: Boolean get() = entityData.get(CHARGING)
    var successfulCasts = 0
        private set

    init { xpReward = 12 }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(CHARGING, false)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(2, RogueMageAttackGoal(this))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.7))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 12f))
        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun finalizeSpawn(level: ServerLevelAccessor, difficulty: DifficultyInstance, reason: EntitySpawnReason, data: SpawnGroupData?): SpawnGroupData? {
        val result = super.finalizeSpawn(level, difficulty, reason, data)
        equip(SpellLootProfile.at(level.level, blockPosition()) ?: SpellLootProfile.RUINS)
        setHomeTo(blockPosition(), 24)
        return result
    }

    /** 服务端出生配置：手持真实地点配方，死亡经原版装备掉落完整保留。 */
    fun equip(profile: SpellLootProfile) {
        setItemSlot(EquipmentSlot.MAINHAND, SpellLoot.wand(profile, random))
        // 由原版装备掉落路径保证只掉一次，并完整保留组件及wandId。
        setGuaranteedDrop(EquipmentSlot.MAINHAND)
    }

    override fun aiStep() {
        super.aiStep()
        if (level() is ServerLevel && isAlive) {
            if (mainHandItem.isEmpty) equip(SpellLootProfile.RUINS) // /summon同样能直接使用。
            WandItem.getWand(mainHandItem)?.tick(this)
            val victim = target
            if (victim is Player && (victim.isCreative || victim.isSpectator)) target = null
            if (victim != null && hasHome() && !isWithinHome(victim.blockPosition())) target = null
        }
    }

    /** AI进入可被伤害打断的准备阶段，并同步持杖姿态。 */
    fun prepareCast() {
        entityData.set(CHARGING, true)
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 0.65f, 1.1f)
    }

    /** AI失去视线、受伤或停止时解除准备姿态。 */
    fun cancelCast() { entityData.set(CHARGING, false) }

    /** 走真实法杖结算；当前地点配方均消耗法力，成功后计数并播放出手提示。 */
    fun releaseCast(): Boolean {
        cancelCast()
        val wand = WandItem.getWand(mainHandItem) ?: return false
        val before = wand.status.mana
        val released = wand.spell(this) && wand.status.mana < before
        if (released) {
            successfulCasts++
            swing(InteractionHand.MAIN_HAND)
            playSound(SoundEvents.EVOKER_CAST_SPELL, 0.65f, 1.2f)
        }
        return released
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val accepted = super.hurtServer(level, source, amount)
        if (accepted) cancelCast()
        return accepted
    }

    override fun getAmbientSound() = SoundEvents.EVOKER_AMBIENT
    override fun getHurtSound(source: DamageSource) = SoundEvents.EVOKER_HURT
    override fun getDeathSound() = SoundEvents.EVOKER_DEATH

    companion object {
        private val CHARGING: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(RogueMageEntity::class.java, EntityDataSerializers.BOOLEAN)
        /** 注册公共侧怪物属性，伤害由所持法杖而非近战属性提供。 */
        fun createAttributes() = createMonsterAttributes().add(Attributes.MAX_HEALTH, 36.0)
            .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 24.0)
            .add(Attributes.ATTACK_DAMAGE, 0.0)

        /** 自然生成只允许指定结构，沿用原版地面与非和平条件。 */
        fun canSpawn(type: EntityType<RogueMageEntity>, level: ServerLevelAccessor, reason: EntitySpawnReason, pos: BlockPos, random: RandomSource): Boolean =
            level.difficulty != Difficulty.PEACEFUL && checkAnyLightMonsterSpawnRules(type, level, reason, pos, random) &&
                (reason != EntitySpawnReason.NATURAL || SpellLootProfile.at(level.level, pos) != null)
    }
}
