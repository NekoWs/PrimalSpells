package work.nekow.primalspells.entity

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import kotlin.math.atan2

/**
 * 测试木桩：用于测试伤害的假人靶子，外形与盔甲架一致（复用盔甲架模型/渲染）。
 *
 * - 血量为 int 上限，永不死亡
 * - 不接受任何治疗（[heal] 无效；负/零伤害视为 0）
 * - 短窗口合并伤害跳字并保留命中次数，显示最近一秒DPS及十秒无操作重置的总伤（见 [DamageNumber]）
 * - 不受击退（靶子保持原地）
 * - 空手右键回收木桩（连同装备掉落，避免物品丢失）；手持物品右键仍可给木桩穿戴
 *
 * 木桩有**两种被动行为**，各由持久化数据里的一个键开启（见 [ATTACK_INTERVAL] 与主手法杖）：
 *
 * 1. **驱动主手法杖的被动**（装了护盾卡就能自己维持护盾）——见 [tick]；
 * 2. **持续攻击玩家**（`/primalspells shielddummy attack`）——见 [driveAttack]。
 */
class TestDummyEntity(
    entityType: net.minecraft.world.entity.EntityType<out TestDummyEntity>,
    level: Level
) : ArmorStand(entityType, level) {

    /** 攻击靶子：下一次开火的冷却 tick。 */
    private var attackCooldown = 0

    /**
     * 攻击靶子专用的法杖实例：**只用来给投射物找粒子管理器**
     * （`Projectile.wand` 在 `Magic.spell()` 里会被读，`lateinit` 不设就会炸）。
     * 与 `DroneEntity.droneWand` 同一套路：不走牌堆、不耗法力。
     */
    private val attackWand = Wand("dummy_attack")

    /** 十秒无伤害后重置的单次测试累计伤害。 */
    var totalDamage = 0.0
        private set
    /** 最近一秒的实际伤害总量，单位伤害/秒。 */
    val dps: Double get() = recent.sumOf { it.second }
    private val recent = ArrayDeque<Pair<Long, Double>>()
    private data class Batch(var damage: Double = 0.0, var hits: Int = 0)
    private val pending = linkedMapOf<work.nekow.primalspells.magic.core.SpellDamageType?, Batch>()
    private var lastDamageTick = Long.MIN_VALUE
    private var nextPopupTick = Long.MAX_VALUE
    private var popupLane = 0
    private var statsDisplay: DummyStatsDisplay? = null

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        if (isRemoved || !isAlive) return false
        if (amount <= 0) return false // 会让生命值增加的"伤害"（治疗）一律归零
        val now = level.gameTime
        if (lastDamageTick != Long.MIN_VALUE && now - lastDamageTick >= 200) resetStats()
        lastDamageTick = now
        totalDamage += amount
        recent.addLast(now to amount.toDouble())
        val type = work.nekow.primalspells.magic.core.SpellDamageType.fromMessageId(source.msgId)
        pending.getOrPut(type) { Batch() }.also { it.damage += amount; it.hits++ }
        if (nextPopupTick == Long.MAX_VALUE) nextPopupTick = now + 5
        if (statsDisplay?.isRemoved != false) {
            statsDisplay = DummyStatsDisplay(this, level).also {
                it.setPos(x, y + bbHeight + 1.7, z)
                it.update(dps, totalDamage)
                level.addFreshEntity(it)
            }
        }
        return true
    }

    /**
     * 每 tick（仅服务端）：驱动主手法杖的被动 + 跑攻击靶子的开火逻辑。
     *
     * 为什么需要驱动法杖：法术的被动（护盾的格挡结算、玻璃表现、法力回复…）都由 `Wand.tick(caster)`
     * 驱动，而那个入口本来是给「玩家手里拿着法杖」用的 —— 木桩没有物品栏 tick，塞了法杖也不会自己转。
     * 这里补上这一下，于是 `/primalspells shielddummy` 造出来的靶子能自己维持护盾，
     * 而且法杖是**真实物品**（存在主手、随存档持久化），重载世界后照样有效。
     */
    override fun tick() {
        super.tick()
        val level = level()
        if (level !is ServerLevel) return
        val stack = getItemInHand(InteractionHand.MAIN_HAND)
        if (stack.item is WandItem) WandItem.getWand(stack)?.tick(this, level)
        driveAttack(level)
        updateDamageStats(level)
    }

    private fun resetStats() {
        totalDamage = 0.0
        recent.clear()
        pending.clear()
        nextPopupTick = Long.MAX_VALUE
        lastDamageTick = Long.MIN_VALUE
        statsDisplay?.update(0.0, 0.0)
    }

    private fun updateDamageStats(level: ServerLevel) {
        val now = level.gameTime
        while (recent.isNotEmpty() && now - recent.first().first >= 20) recent.removeFirst()
        if (lastDamageTick != Long.MIN_VALUE && now - lastDamageTick >= 200) resetStats()
        if (now >= nextPopupTick) {
            // 5刻收集窗口合并同类伤害；×N保留命中次数，各批在不同横向槽位升起。
            for ((type, batch) in pending) {
                val lane = popupLane++ % 5 - 2
                spawnDamageNumber(level, position().add(0.0, bbHeight + 0.15 + (popupLane % 2) * 0.15, 0.0),
                    batch.damage, type, batch.hits, lane * 0.6f)
            }
            pending.clear()
            nextPopupTick = Long.MAX_VALUE
        }
        if (now % 4L == 0L) statsDisplay?.update(dps, totalDamage)
    }

    /**
     * 攻击靶子：每 tick 索敌并瞄准最近的玩家，按 [ATTACK_INTERVAL] 直接施放 [ATTACK_SPELL]。
     *
     * 走的是 `DroneEntity` 那条路（自己构造投射物 + `MagicManager.add`），**不经过牌堆与法力** ——
     * 这条指令的用途是「稳定可复现地向玩家开火」，不是模拟一个真的施法者：
     * 牌堆/冷却/法力都会让开火节奏变得不可预期。
     *
     * 索敌**不排除创造/旁观玩家**（与无人机的 PvP 规则不同）：站在创造模式里挨打正是它的用法。
     */
    private fun driveAttack(level: ServerLevel) {
        val interval = persistentData.getInt(ATTACK_INTERVAL).orElse(0)
        if (interval <= 0) return
        if (attackCooldown > 0) attackCooldown--

        val target = level.getEntitiesOfClass(ServerPlayer::class.java, boundingBox.inflate(ATTACK_RANGE)) {
            it.isAlive && !it.isRemoved
        }.minByOrNull { distanceToSqr(it) } ?: return

        val eye = eyePosition
        val aim = target.position().add(0.0, target.bbHeight * 0.6, 0.0)
        val dir = aim.subtract(eye)
        if (dir.lengthSqr() < 1e-9) return
        val unit = dir.normalize()

        // 让它「看着你」：朝向换算抄 `DroneEntity`（atan2(dz, dx) − 90°），两处口径保持一致
        yRot = Math.toDegrees(atan2(unit.z, unit.x)).toFloat() - 90f
        yHeadRot = yRot
        yBodyRot = yRot

        if (attackCooldown > 0) return
        attackCooldown = interval

        val id = persistentData.getString(ATTACK_SPELL).orElse(DEFAULT_ATTACK_SPELL)
        val projectile = MagicManager.create(id) as? Projectile ?: return
        projectile.caster = this
        projectile.wand = attackWand
        projectile.position = Vector3d(eye.x, eye.y, eye.z)
        projectile.velocity = Vector3f(unit.x.toFloat(), unit.y.toFloat(), unit.z.toFloat())
        projectile.spell()
        MagicManager.add(projectile)
    }

    /** 木桩不接受任何治疗效果 */
    override fun heal(amount: Float) {}

    /** 木桩不受击退，保持原地便于连续测试（5 参数版会委托到此版） */
    override fun knockback(power: Double, xd: Double, zd: Double, source: DamageSource, damage: Float, comesFromEffect: Boolean) {}

    /** 法术和爆炸直接push，不经过原版knockback；靶子仍只记录伤害，不接受推力。 */
    override fun push(x: Double, y: Double, z: Double) {}

    /** 空手右键：回收木桩（掉落木桩物品 + 全部装备）；否则保持盔甲架穿戴行为 */
    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        if (!player.getItemInHand(hand).isEmpty) {
            return super.interact(player, hand, location)
        }
        if (level().isClientSide) return InteractionResult.SUCCESS_SERVER
        val serverLevel = level() as ServerLevel
        Block.popResource(serverLevel, blockPosition(), ItemStack(ModItems.TEST_DUMMY.get()))
        for (slot in EquipmentSlot.VALUES) {
            val stack = getItemBySlot(slot)
            if (!stack.isEmpty) Block.popResource(serverLevel, blockPosition(), stack)
        }
        kill(serverLevel)
        return InteractionResult.SUCCESS_SERVER
    }

    companion object {
        /** 攻击靶子：开火间隔（tick）的持久化键；缺失或 ≤ 0 = 不攻击。 */
        const val ATTACK_INTERVAL = "primalspells_dummy_attack_interval"

        /** 攻击靶子：要施放的法术 id 的持久化键。 */
        const val ATTACK_SPELL = "primalspells_dummy_attack_spell"

        /** 攻击靶子的缺省法术。 */
        const val DEFAULT_ATTACK_SPELL = "fireball"

        /** 索敌半径（格）。 */
        private const val ATTACK_RANGE = 32.0

        /** 血量为 int 上限 */
        fun createAttributes(): AttributeSupplier.Builder =
            ArmorStand.createAttributes().add(Attributes.MAX_HEALTH, Int.MAX_VALUE.toDouble())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
    }
}
