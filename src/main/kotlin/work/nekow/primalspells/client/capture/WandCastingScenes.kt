package work.nekow.primalspells.client.capture

import net.minecraft.client.Minecraft
import work.nekow.primalspells.client.render.WandModelProperty
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
    fun all(): List<Scene> = listOf(casting(), casting(true), casting(true, 90), casting(front = true), WandAimScenes.aiming())

    private fun casting(firstPerson: Boolean = false, fov: Int = 60, front: Boolean = false): Scene {
        val name = if (front) "wand_casting_front" else if (fov == 90) "wand_casting_wide" else if (firstPerson) "wand_casting_first_person" else "wand_casting"
        val pitch = if (front) -15f else 30f
        lateinit var first: Wand
        lateinit var second: Wand
        var firstStack = ItemStack.EMPTY
        var secondStack = ItemStack.EMPTY
        var earlyBlocked = false
        var firstCast = false
        var gemOriginMatches = false
        var repeatCast = false
        var switchBlocked = false
        var switchCast = false
        var offhandBlocked = false
        var offhandCast = false
        val clientChecks = java.util.concurrent.ConcurrentSkipListMap<String, Boolean>()
        var firstFrame = -1f
        fun sampleModel(t: Int) {
            Minecraft.getInstance().execute {
                val mc = Minecraft.getInstance(); val player = mc.player ?: return@execute
                val stack = if (t >= 160) player.offhandItem else player.mainHandItem
                val frame = WandModelProperty().get(stack, mc.level, player, 0)
                val light = WandModelProperty(true).get(stack, mc.level, player, 0)
                when(t) {
                    20 -> firstFrame = frame
                    25 -> clientChecks["native-idle-module-frame-changes"] = frame != firstFrame && firstFrame >= 0f
                    30 -> clientChecks["successful-cast-lights-crystal"] = light > 0f
                    40 -> clientChecks["crystal-afterglow-is-short"] = light == 0f
                    180 -> clientChecks["offhand-cast-lights-crystal"] = light > 0f
                }
            }
        }
        fun equip(): Pair<ItemStack, Wand> {
            val stack = ItemStack(ModItems.WAND.get())
            val wand = WandManager[WandManager.createWand(stack)]
            wand.magics += MagicManager.create("fireball")
            wand.delaySeconds = 0.8; wand.rechargeSeconds = 1.8
            wand.load(); wand.status.mana = wand.mana
            return stack to wand
        }
        return Scene(name, "持杖三角法阵 / 悬浮符印 / 施放间隔与充能", 24, 220,
            thirdPerson = !firstPerson, thirdPersonFront = front, fov = fov, showHands = firstPerson,
            prepare = { ctx ->
                val floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(false, 0.5, floor+1.0, 0.5, 0f, pitch)
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
                Pose(x, ctx.floorBlockY(0, 0)+1.0, 0.5, if (t in 75..130) (t-75)*1.2f else 0f, pitch)
            }, tick = { ctx, t ->
                if (t in listOf(20,25,30,40,180)) sampleModel(t)
                if (t == 8) ctx.player.setItemInHand(InteractionHand.MAIN_HAND, firstStack)
                if (t == 9) {
                    val mana = first.status.mana
                    earlyBlocked = !first.spell(ctx.player) && first.status.mana == mana && first.status.delay == 0.0
                }
                if (t == 28) {
                    val gem = WandCastingFx.origin(ctx.player, first)
                    firstCast = first.spell(ctx.player)
                    // add走pendingAdd；在下一Pre tick并入活动表后、第一次Move之前验证真实出生点。
                    work.nekow.primalspells.util.Deferred.after(ctx.level, 0) {
                        val shot = MagicManager.projectiles.lastOrNull { it.wand === first }
                        gemOriginMatches = firstCast && shot != null && shot.position.distance(gem.toVector3f()) < 0.0001f
                    }
                }
                // 等第一轮充能收尾结束再重复施法，避免将下一轮柱体当成退场残留。
                if (t == 78) repeatCast = first.spell(ctx.player)
                if (t == 95) ctx.player.setItemInHand(InteractionHand.MAIN_HAND, secondStack)
                if (t == 96) switchBlocked = !second.spell(ctx.player)
                if (t == 115) switchCast = second.spell(ctx.player)
                // 正面抬头取景的弹体飞行更久，提前结束弹道尾迹，独立测量持杖退场。
                if (front && t == 128) MagicManager.projectiles.forEach { it.alive = false }
                if (t == 140) {
                    ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                    MagicManager.projectiles.forEach { it.alive = false }
                }
                if (t == 160) ctx.player.setItemInHand(InteractionHand.OFF_HAND, firstStack)
                if (t == 161) offhandBlocked = !first.spell(ctx.player)
                if (t == 178) offhandCast = first.spell(ctx.player)
                if (t == 180) MagicManager.projectiles.forEach { it.alive = false }
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
                35 -> listOf(Check(name, t, "projectile-starts-at-gem", gemOriginMatches, "matches=$gemOriginMatches"), Check(name, t, "cast-ready-and-both-cooldowns-running", firstCast && first.status.delay > 0 && first.status.recharge > 0 && WandCastingFx.ready(ctx.player, first), "cast=$firstCast delay=${first.status.delay} recharge=${first.status.recharge}"))
                80 -> listOf(Check(name, t, "repeated-cast-does-not-restart-equip", repeatCast && WandCastingFx.ready(ctx.player, first), "cast=$repeatCast"))
                120 -> listOf(Check(name, t, "switch-wand-restarts-equip-only-once", switchBlocked && switchCast && WandCastingFx.ready(ctx.player, second), "blocked=$switchBlocked cast=$switchCast"))
                144 -> listOf(Check(name, t, "put-away-keeps-finite-exit-animation", ParticleManager.of(ctx.level).getEngine().particleCount() > 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                148 -> listOf(Check(name, t, "put-away-cleans-after-exit", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                192 -> listOf(Check(name, t, "offhand-exit-still-visible", ParticleManager.of(ctx.level).getEngine().particleCount() > 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                208 -> listOf(Check(name, t, "offhand-exit-cleaned", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                180 -> listOf(Check(name, t, "offhand-has-equip-and-casting", offhandBlocked && offhandCast, "blocked=$offhandBlocked cast=$offhandCast"))
                219 -> listOf(Check(name, t, "projectiles-cleaned", MagicManager.projectiles.isEmpty(), "shots=${MagicManager.projectiles.size}"), Check(name, t, "pd-particles-zero", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")) + clientChecks.map { (label, ok) -> Check(name, t, label, ok, "result=$ok") }
                else -> emptyList()
            } })
    }
}
