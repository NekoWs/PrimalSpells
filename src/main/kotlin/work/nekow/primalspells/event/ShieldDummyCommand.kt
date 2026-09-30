package work.nekow.primalspells.event

import com.mojang.brigadier.arguments.IntegerArgumentType
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
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.registry.ModEntities
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.WandManager

/**
 * **测试指令**：`/primalspells shielddummy [cards]` —— 在视线方向放一个**持盾靶子**。
 *
 * ## 为什么需要它
 *
 * 护盾的表现（受击处那块玻璃板、破盾一闪而碎、恢复淡闪）只能在游戏里看手感，而这个模组没有方便的
 * 「造一个会挨打的盾」的手段：调 `TestDummyItem` 放出来的木桩是空手的，法杖得手工塞、塞了也**不会被动驱动**。
 * 这条指令把三件事一次做完：生成木桩 → 往它主手塞一根装了护盾卡的法杖 → 让木桩自己驱动那根法杖
 * （见 [TestDummyEntity.tick]，被动驱动才有护盾结算与玻璃表现）。
 *
 * ## 用法
 *
 * ```
 * /primalspells shielddummy          在视线方向 3 格处放一个持盾靶子（1 张护盾卡，容量 100）
 * /primalspells shielddummy 3        3 张护盾卡（容量 300，破盾更耐打，方便连着看玻璃板）
 * /primalspells shielddummy clear    清掉所有由本指令生成的靶子
 * ```
 *
 * 生成之后**用手上的法术打它**就能看到：平时它身上什么都没有 → 挡下的那一下在受击侧亮起一块白玻璃 →
 * 耐久打空时整罩一闪而碎 → 50 tick 后护盾恢复时全身淡闪一下。
 *
 * ## 边界
 *
 * - 只做生成与清理，不改任何结算；靶子依然是 [TestDummyEntity]（永不死亡、不受击退）；
 * - 清理由指令的 `clear` 完成（按 [TAG] 标记找），重载世界后 `clear` 只能清当前维度已加载的靶子；
 * - 这是**开发/测试用**指令：注册在公共事件总线上，权限沿用原版的「游戏管理员」级别
 *   （`Permissions.COMMANDS_GAMEMASTER`，与 `/gamemode`、`/summon` 同级）。
 */
object ShieldDummyCommand {

    /** 标记生成出来的靶子，供 `clear` 反查（写在实体的持久化数据里，重载后仍能认出来）。 */
    private const val TAG = "primalspells_shield_dummy"

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
                        ),
                ),
        )
    }

    /** 在视线方向 3 格处放一个持盾靶子；[cards] 张护盾卡决定耐久上限（每张 100）。 */
    private fun spawn(source: CommandSourceStack, cards: Int): Int {
        val level = source.level
        val eye = source.position
        val look = source.entity?.lookAngle ?: net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0)
        val feet = eye.add(look.x * SPAWN_DISTANCE, 0.0, look.z * SPAWN_DISTANCE)
        val groundY = level.getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            net.minecraft.util.Mth.floor(feet.x),
            net.minecraft.util.Mth.floor(feet.z),
        ).toDouble()

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
        repeat(cards) { wand.magics += MagicManager.create("energy_shield") as EnergyShield }
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
                    "持盾靶子已生成：护盾卡 $cards 张（耐久上限 ${(100.0 * cards).toInt()}），" +
                        "站在 ${"%.1f".format(feet.x)}, ${"%.1f".format(groundY)}, ${"%.1f".format(feet.z)}；用手上的法术打它试试。"
                )
            },
            false,
        )
        return 1
    }

    /** 清掉当前维度里所有由本指令生成的靶子。 */
    private fun clear(source: CommandSourceStack): Int {
        val level: ServerLevel = source.level
        val doomed = level.allEntities.filter { it is TestDummyEntity && it.persistentData.getBoolean(TAG).orElse(false) }
        doomed.forEach { it.discard() }
        source.sendSuccess({ Component.literal("已清理 ${doomed.size} 个持盾靶子") }, false)
        return doomed.size
    }

    /** 生成距离（眼位沿视线方向，水平投影）。 */
    private const val SPAWN_DISTANCE = 3.0
}
