package work.nekow.primalspells.event

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.Permissions
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.wand.WandManager
import work.nekow.primalspells.wand.WandTier
import work.nekow.primalspells.wand.WandVariant

/**
 * **测试指令**：`/primalspells wand <等级> [charged]` —— 直接给自己一根指定等级的法杖。
 *
 * ## 为什么需要它
 *
 * 法杖等级（[WandTier]）目前唯一的获取途径是创造模式物品栏 —— 而"每一级的数值是否合理"只能靠实机对比。
 * 这条指令把「拿到某一级 + 立刻掷出数值」一次做完，并把掷出的结果回报出来（法力/回蓝/双延迟/槽位/抽牌），
 * 不用去翻物品 LORE。
 *
 * 自然的获取途径（战利品掉落/合成）**尚未实现**：现阶段的定位是"先能拿到、先能测"，
 * 掉落按用户口径放到下一轮。
 *
 * ## 用法
 *
 * ```
 * /primalspells wand t5              大师杖（300 法力 / 100 回蓝 / 240 ms 双延迟 / 14 槽）
 * /primalspells wand t1 charged      朽木杖的蓄能型（蓝 ×3、回蓝 ÷3、延迟 ×1.5）
 * /primalspells wand debug           调试杖（3000 / 1000 / 0，等价于创造模式那根）
 * ```
 *
 * ## 边界
 *
 * - 只发物品、不改任何结算；数值由 `WandManager.createWand` 按等级掷出（与掉落/创造模式同一条路）；
 * - 权限沿用原版「游戏管理员」级别（`Permissions.COMMANDS_GAMEMASTER`）。
 */
object WandCommand {

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("primalspells")
                .requires(Commands.hasPermission(PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                .then(
                    Commands.literal("wand")
                        .then(
                            Commands.argument("tier", StringArgumentType.word())
                                .suggests(SUGGESTIONS)
                                .executes { ctx ->
                                    give(ctx.source, StringArgumentType.getString(ctx, "tier"), false)
                                }
                                .then(
                                    Commands.literal("charged").executes { ctx ->
                                        give(ctx.source, StringArgumentType.getString(ctx, "tier"), true)
                                    },
                                ),
                        ),
                ),
        )
    }

    /** 发一根 [tierName] 等级（可选蓄能型）的法杖，并把掷出的数值回报出来。 */
    private fun give(source: CommandSourceStack, tierName: String, charged: Boolean): Int {
        val tier = WandTier.byName(tierName)
        if (tier == null) {
            source.sendFailure(Component.literal("没有这个法杖等级：$tierName（可用：t1~t6、debug）"))
            return 0
        }
        val player = source.player
        if (player == null) {
            source.sendFailure(Component.literal("这条指令需要由玩家执行（要往背包里放东西）"))
            return 0
        }
        val variant = if (charged) WandVariant.CHARGED else WandVariant.NORMAL
        val stack = ModItems.wandStack(tier, variant)
        // 立刻创建法杖实例：数值在创建时掷出（抽牌数是随机的），这样回报给对方的就是**这一根杖**的真实数值，
        // 而不是等级表上的标称值。之后的首次 tick 会命中同一个 wand id，不会重掷。
        val wand = WandManager.createWand(stack).let { id -> WandManager[id] }
        if (!player.inventory.add(stack)) player.drop(stack, false)

        val name = if (variant.suffixKey != null) "${tier.itemName} · 蓄能" else tier.itemName
        source.sendSuccess(
            {
                Component.literal(
                    "已获得 $name：法力 ${Lore.formatDouble(wand.mana)} / 回蓝 ${Lore.formatDouble(wand.charge)}每秒" +
                        " / 双延迟 ${Lore.formatDouble(wand.delayMs)} ms / ${wand.size} 槽 / 抽牌 ${wand.cast}"
                )
            },
            false,
        )
        return 1
    }

    /** 补全候选：六个等级 + 调试杖。 */
    private val SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        (WandTier.LADDER.map { "t${it.level}" } + "debug").forEach(builder::suggest)
        builder.buildFuture()
    }
}
