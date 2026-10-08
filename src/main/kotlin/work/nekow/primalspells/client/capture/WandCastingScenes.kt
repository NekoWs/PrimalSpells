package work.nekow.primalspells.client.capture

import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.visual.WandCastingFx
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager
import kotlin.math.sin

/** 真玩家持有真实法杖：切换预热、待机、移动跟随、两种冷却与放下清理。 */
object WandCastingScenes {
    fun all(): List<Scene> = listOf(casting(), casting(true))

    private fun casting(firstPerson: Boolean = false): Scene {
        val name = if (firstPerson) "wand_casting_first_person" else "wand_casting"
        lateinit var first: Wand
        lateinit var second: Wand
        var firstStack = ItemStack.EMPTY
        var secondStack = ItemStack.EMPTY
        var earlyBlocked = false
        var firstCast = false
        var repeatCast = false
        var switchBlocked = false
        var switchCast = false
        var offhandBlocked = false
        var offhandCast = false
        fun equip(): Pair<ItemStack, Wand> {
            val stack = ItemStack(ModItems.WAND.get())
            val wand = WandManager[WandManager.createWand(stack)]
            wand.magics += MagicManager.create("fireball")
            wand.delaySeconds = 0.8; wand.rechargeSeconds = 1.8
            wand.load(); wand.status.mana = wand.mana
            return stack to wand
        }
        return Scene(name, "持杖三角法阵 / 待机汇聚 / 施放间隔与充能", 24, 220,
            thirdPerson = !firstPerson, fov = 60,
            prepare = { ctx ->
                val floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(false, 0.5, floor+1.0, 0.5, 0f, 30f)
                ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
                WandCastingFx.clear()
                val a = equip(); firstStack = a.first; first = a.second
                val b = equip(); secondStack = b.first; second = b.second
                // 预热阶段也属于玩家背包，避免内存回收后测试还握着已卸载的旧Wand引用。
                ctx.player.inventory.setItem(7, firstStack)
                ctx.player.inventory.setItem(8, secondStack)
            }, camera = { ctx, t ->
                val x = if (t in 75..130) 0.5 + sin((t-75)*0.04)*1.5 else 0.5
                Pose(x, ctx.floorBlockY(0, 0)+1.0, 0.5, if (t in 75..130) (t-75)*1.2f else 0f, 30f)
            }, tick = { ctx, t ->
                if (t == 8) ctx.player.setItemInHand(InteractionHand.MAIN_HAND, firstStack)
                if (t == 9) {
                    val mana = first.status.mana
                    earlyBlocked = !first.spell(ctx.player) && first.status.mana == mana && first.status.delay == 0.0
                }
                if (t == 28) firstCast = first.spell(ctx.player)
                if (t == 72) repeatCast = first.spell(ctx.player)
                if (t == 95) ctx.player.setItemInHand(InteractionHand.MAIN_HAND, secondStack)
                if (t == 96) switchBlocked = !second.spell(ctx.player)
                if (t == 115) switchCast = second.spell(ctx.player)
                if (t == 140) {
                    ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                    MagicManager.projectiles.forEach { it.alive = false }
                }
                if (t == 155) ctx.player.setItemInHand(InteractionHand.OFF_HAND, firstStack)
                if (t == 156) offhandBlocked = !first.spell(ctx.player)
                if (t == 175) offhandCast = first.spell(ctx.player)
                if (t == 190) {
                    ctx.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
                    MagicManager.projectiles.forEach { it.alive = false }
                }
            }, finish = { ctx ->
                ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
                ctx.player.inventory.setItem(7, ItemStack.EMPTY)
                ctx.player.inventory.setItem(8, ItemStack.EMPTY)
                WandCastingFx.forget(ctx.player.uuid)
            }, checksAt = { ctx, t -> when(t) {
                12 -> listOf(Check(name, t, "equip-delay-blocks-without-spending", earlyBlocked, "blocked=$earlyBlocked"))
                35 -> listOf(Check(name, t, "cast-ready-and-both-cooldowns-running", firstCast && first.status.delay > 0 && first.status.recharge > 0 && WandCastingFx.ready(ctx.player, first), "cast=$firstCast delay=${first.status.delay} recharge=${first.status.recharge}"))
                80 -> listOf(Check(name, t, "repeated-cast-does-not-restart-equip", repeatCast && WandCastingFx.ready(ctx.player, first), "cast=$repeatCast"))
                120 -> listOf(Check(name, t, "switch-wand-restarts-equip-only-once", switchBlocked && switchCast && WandCastingFx.ready(ctx.player, second), "blocked=$switchBlocked cast=$switchCast"))
                150 -> listOf(Check(name, t, "put-away-cleans-persistent-groups", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                180 -> listOf(Check(name, t, "offhand-has-equip-and-casting", offhandBlocked && offhandCast, "blocked=$offhandBlocked cast=$offhandCast"))
                219 -> listOf(Check(name, t, "projectiles-cleaned", MagicManager.projectiles.isEmpty(), "shots=${MagicManager.projectiles.size}"), Check(name, t, "pd-particles-zero", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                else -> emptyList()
            } })
    }
}
