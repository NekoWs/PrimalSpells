package work.nekow.primalspells.event

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModEntities
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.WandManager

/**
 * **测试指令**：`/primalspells shielddummy ...` —— 造两种靶子：
 * **持盾靶子**（[spawn]）与**持续攻击你的靶子**（[spawnAttacker]）。
 *
 * ## 为什么需要它
 *
 * 护盾与防御手感只能在游戏里看，而这个模组没有方便的「造一个会挨打的盾 / 会打你的敌人」的手段：
 * 调 `TestDummyItem` 放出来的木桩是空手的，法杖得手工塞、塞了也**不会被动驱动**。
 * 这条指令把「生成木桩 → 装法杖/装攻击 → 让木桩自己动起来」一次做完
 * （被动驱动见 [TestDummyEntity.tick]）。
 *
 * ## 用法
 *
 * ```
 * /primalspells shielddummy                在视线方向 3 格处放一个持盾靶子（1 张护盾卡，抗性 4）
 * /primalspells shielddummy 3              3 张护盾卡（抗性 8：普通火球/激光都落在抗性以内，只付基础 5 MP）
 * /primalspells shielddummy attack         放一个每 20 tick 朝你开一发火球的靶子（自动瞄准 32 格内最近的玩家）
 * /primalspells shielddummy attack laser   同上，但打激光（`delay -10` 那种连发法术也照样按间隔打）
 * /primalspells shielddummy attack fireball 10   指定开火间隔（tick）
 * /primalspells shielddummy clear          清掉所有由本指令生成的靶子（两种都清）
 * ```
 *
 * 打持盾靶子能看到：平时它身上什么都没有 → 挡下的那一下在受击侧亮起一块白玻璃 →
 * 大威力法术（大爆强度 24×6=144）在法力不够时会打穿，受击侧那一圈碎裂并留下 3 秒的淡红破口轮廓。
 * 结算口径见 [docs/design/combat-pricing.md] §3–§4。攻击靶子则是「第一视角看自己的护盾」用的。
 *
 * ## 边界
 *
 * - 只做生成与清理，不改任何结算；两种靶子都是 [TestDummyEntity]（永不死亡、不受击退）；
 * - 攻击靶子**不走牌堆与法力**（直接构造投射物，与 `DroneEntity` 同一条路），
 *   所以开火节奏完全由 `interval` 决定，可复现；
 * - 清理由 `clear` 完成（按 [TAG] 标记找），重载世界后 `clear` 只能清当前维度已加载的靶子；
 * - 这是**开发/测试用**指令：注册在公共事件总线上，权限沿用原版的「游戏管理员」级别
 *   （`Permissions.COMMANDS_GAMEMASTER`，与 `/gamemode`、`/summon` 同级）。
 */
object ShieldDummyCommand {

    /** 标记生成出来的靶子，供 `clear` 反查（写在实体的持久化数据里，重载后仍能认出来）。 */
    private const val TAG = "primalspells_shield_dummy"

    /** 生成距离（眼位沿视线方向，水平投影）。 */
    private const val SPAWN_DISTANCE = 3.0

    /** 攻击靶子的缺省开火间隔（tick）。 */
    private const val DEFAULT_ATTACK_INTERVAL = 20

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("primalspells")
                .requires(Commands.hasPermission(PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                .then(
                    Commands.literal("shielddummy")
                        .executes { ctx -> spawn(ctx.source, 1) }
                        .then(
                            Commands.literal("clear").executes { ctx -> clear(ctx.source) },
                        )
                        .then(
                            Commands.argument("cards", IntegerArgumentType.integer(1, 8))
                                .executes { ctx ->
                                    spawn(ctx.source, IntegerArgumentType.getInteger(ctx, "cards"))
                                },
                        )
                        .then(
                            Commands.literal("attack")
                                .executes { ctx ->
                                    spawnAttacker(
                                        ctx.source,
                                        TestDummyEntity.DEFAULT_ATTACK_SPELL,
                                        DEFAULT_ATTACK_INTERVAL,
                                    )
                                }
                                .then(
                                    Commands.argument("spell", StringArgumentType.word())
                                        .suggests(SPELL_SUGGESTIONS)
                                        .executes { ctx ->
                                            spawnAttacker(
                                                ctx.source,
                                                StringArgumentType.getString(ctx, "spell"),
                                                DEFAULT_ATTACK_INTERVAL,
                                            )
                                        }
                                        .then(
                                            Commands.argument("interval", IntegerArgumentType.integer(1, 200))
                                                .executes { ctx ->
                                                    spawnAttacker(
                                                        ctx.source,
                                                        StringArgumentType.getString(ctx, "spell"),
                                                        IntegerArgumentType.getInteger(ctx, "interval"),
                                                    )
                                                },
                                        ),
                                ),
                        ),
                ),
        )
    }

    /** 在视线方向 3 格处放一个持盾靶子；[cards] 张护盾卡决定抗性（基础 2 + 每张 2）。 */
    private fun spawn(source: CommandSourceStack, cards: Int): Int {
        val level = source.level
        val (feet, groundY) = landingSpot(source)

        val dummy = TestDummyEntity(ModEntities.TEST_DUMMY.get(), level)
        dummy.setPos(feet.x, groundY, feet.z)
        dummy.setYRot(source.entity?.yRot ?: 0f)
        dummy.setNoGravity(false)
        dummy.setInvulnerable(true)
        dummy.persistentData.putBoolean(TAG, true)
        // 装一根「只放护盾卡」的法杖并塞进主手：木桩自己的 tick 会驱动它（见 TestDummyEntity）。
        // ⚠️ 必须走 `WandManager` 拿**正牌实例**：`WandItem.getWand(stack)` 是按 `wand_id` 去
        // `WandManager` 里查的，自己 `Wand(...)` 造一个再塞组件，查回来会是另一个空实例（实测 shields=0）。
        val stack = ItemStack(ModItems.WAND.get())
        val wand = WandManager.createWand(stack).let { id -> WandManager[id] }
        repeat(cards) { wand.magics += SpellRegistry.create("energy_shield") as EnergyShield }
        wand.load()
        dummy.setItemInHand(InteractionHand.MAIN_HAND, stack)
        if (!level.addFreshEntity(dummy)) {
            source.sendFailure(Component.literal("生成失败：这个位置放不下靶子"))
            return 0
        }
        // 立刻驱动一次：让护盾本 tick 就上线（否则要等木桩下一次 tick）
        wand.tick(dummy, level)
        source.sendSuccess(
            {
                Component.literal(
                    "持盾靶子已生成：护盾卡 $cards 张（抗性 ${(2 + 2 * cards)}），" +
                        "站在 ${"%.1f".format(feet.x)}, ${"%.1f".format(groundY)}, ${"%.1f".format(feet.z)}；用手上的法术打它试试。"
                )
            },
            false,
        )
        return 1
    }

    /**
     * 放一个**持续攻击玩家**的靶子：每 [interval] tick 朝 32 格内最近的玩家打一发 [spellId]。
     *
     * 法术先校验（必须是注册过的投射物），否则只发一条失败消息、不生成实体 —— 免得留下一个永远不开火的靶子。
     */
    private fun spawnAttacker(source: CommandSourceStack, spellId: String, interval: Int): Int {
        val level = source.level
        if (SpellRegistry.create(spellId) !is Projectile) {
            source.sendFailure(Component.literal("没有这个投射物法术：$spellId"))
            return 0
        }
        val (feet, groundY) = landingSpot(source)

        val dummy = TestDummyEntity(ModEntities.TEST_DUMMY.get(), level)
        dummy.setPos(feet.x, groundY, feet.z)
        // 出生时先面朝施令者（开火时每 tick 还会重新瞄准，这里只是让第一帧不难看）
        dummy.setYRot((source.entity?.yRot ?: 0f) + 180f)
        dummy.setNoGravity(false)
        dummy.setInvulnerable(true)
        dummy.persistentData.putBoolean(TAG, true)
        dummy.persistentData.putString(TestDummyEntity.ATTACK_SPELL, spellId)
        dummy.persistentData.putInt(TestDummyEntity.ATTACK_INTERVAL, interval)
        if (!level.addFreshEntity(dummy)) {
            source.sendFailure(Component.literal("生成失败：这个位置放不下靶子"))
            return 0
        }
        source.sendSuccess(
            {
                Component.literal(
                    "攻击靶子已生成：每 $interval tick 一发「$spellId」，自动瞄准 32 格内最近的玩家；" +
                        "站在 ${"%.1f".format(feet.x)}, ${"%.1f".format(groundY)}, ${"%.1f".format(feet.z)}。"
                )
            },
            false,
        )
        return 1
    }

    /** 视线方向 3 格、按高度图贴地的落点（两种靶子共用）。 */
    private fun landingSpot(source: CommandSourceStack): Pair<net.minecraft.world.phys.Vec3, Double> {
        val eye = source.position
        val look = source.entity?.lookAngle ?: net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0)
        val feet = eye.add(look.x * SPAWN_DISTANCE, 0.0, look.z * SPAWN_DISTANCE)
        val groundY = source.level.getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            net.minecraft.util.Mth.floor(feet.x),
            net.minecraft.util.Mth.floor(feet.z),
        ).toDouble()
        return feet to groundY
    }

    /** 清掉当前维度里所有由本指令生成的靶子（持盾的与攻击的一起清）。 */
    private fun clear(source: CommandSourceStack): Int {
        val level: ServerLevel = source.level
        val doomed = level.allEntities.filter { it is TestDummyEntity && it.persistentData.getBoolean(TAG).orElse(false) }
        doomed.forEach { it.discard() }
        source.sendSuccess({ Component.literal("已清理 ${doomed.size} 个靶子") }, false)
        return doomed.size
    }

    /** 补全候选：所有注册过的**投射物**法术 id（攻击靶子只能打投射物）。 */
    private val SPELL_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SpellRegistry.factories.keys
            .filter { SpellRegistry.create(it) is Projectile }
            .sorted()
            .forEach(builder::suggest)
        builder.buildFuture()
    }
}
