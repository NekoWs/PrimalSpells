package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.MoverType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus
import work.nekow.primalspells.magic.effect.visual.WandCastingFx
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.registry.ModEffects
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager

/** 真实持杖进入虚空，观察双端锁位、法阵零残留以及按85t到期后的重新展开。 */
object VoidPhaseScene {
    fun scene(): Scene {
        val name = "void_phase"
        val checks = ConcurrentHashMap<Int, MutableList<Check>>()
        lateinit var wand: Wand
        var stack = ItemStack.EMPTY
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { java.util.concurrent.CopyOnWriteArrayList() }.add(Check(name, t, id, ok, detail))
        }
        return Scene(name, "虚空锁位 / 隐藏法阵 / 寿命恢复", 24, 180, thirdPerson = true, moveCamera = false,
            prepare = { ctx ->
                checks.clear(); WandCastingFx.clear()
                val floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor); ctx.setup(false, .5, floor + 1.0, .5, 0f, 65f)
                ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
            }, camera = { ctx, _ -> Pose(.5, ctx.floorBlockY(0, 0) + 1.0, .5, 0f, 65f) },
            tick = { ctx, t ->
                if (t == 8) {
                    // 出生即放入背包，避免预热期尚未交给玩家的测试物品被正常闲置回收。
                    stack = ItemStack(ModItems.WAND.get())
                    wand = WandManager[WandManager.createWand(stack)].also {
                        it.cast = 1; it.mana = 1000.0; it.charge = 0.0
                        it.magics += listOf("extended_lifetime", "void_escape").map { id -> SpellRegistry.create(id)!! }
                        it.load(); it.status.mana = 1000.0
                    }
                    ctx.player.setItemInHand(InteractionHand.MAIN_HAND, stack)
                }
                if (t == 32) record(t, "void-held-wand-casts-85t", wand.spell(ctx.player) && ctx.player.getEffect(ModEffects.VOID_PHASE)?.duration == 85, "duration=${ctx.player.getEffect(ModEffects.VOID_PHASE)?.duration}; failure=${wand.status.failedReason}")
                if (t in 40..110) {
                    val player = ctx.player; val before = player.position()
                    player.deltaMovement = Vec3(.3, .5, -.4)
                    player.move(MoverType.SELF, Vec3(.2, -.2, .1))
                    if (t in listOf(40, 80, 110)) record(t, "void-server-player-locked-$t", VoidStatus.isPhased(player) && player.position() == before && player.deltaMovement == Vec3.ZERO, "${player.position()}")
                }
                if (t == 60) {
                    val player = ctx.player; val before = player.position()
                    // 纠正位置必须产生新传送ID；原版等待传送分支只接收转头，不能使此检查误通过。
                    val teleport = net.minecraft.server.network.ServerGamePacketListenerImpl::class.java.getDeclaredField("awaitingTeleport").apply { isAccessible = true }
                    val beforeId = teleport.getInt(player.connection)
                    player.connection.handleMovePlayer(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(before.add(2.0, 1.0, 0.0), 17f, 10f, false, false))
                    record(t, "void-server-rejects-position-packet-keeps-look", player.position() == before && player.yRot == 17f && teleport.getInt(player.connection) != beforeId, "${player.position()}; yaw=${player.yRot}; correction=${teleport.getInt(player.connection) != beforeId}")
                }
                if (t == 120) {
                    record(t, "void-player-expires-with-adjusted-duration", !VoidStatus.isPhased(ctx.player), "effect=${ctx.player.getEffect(ModEffects.VOID_PHASE)}")
                    val before = ctx.player.position(); ctx.player.move(MoverType.SELF, Vec3(.25, 0.0, 0.0))
                    record(t, "void-player-movement-restores-on-expiry", ctx.player.position().distanceTo(before) > .2, "${ctx.player.position()}")
                }
                if (t == 140) ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            }, clientTick = { mc, t ->
                if (t in listOf(28, 44, 80, 110, 135, 175)) {
                    var particles = 0
                    ClientParticleEngine.instance()?.forEachParticleSample(0f) { if (it.alpha > .01f && it.scaleX * it.scaleY > .00001f) particles++ }
                    val expectedVisible = t == 28 || t == 135
                    record(t, "void-native-sigil-visibility-$t", if (expectedVisible) particles >= 96 else particles == 0, "visible=$particles; base=72 edges+24 corners")
                    if (t == 44) {
                        val player = mc.player!!; val position = player.position()
                        player.deltaMovement = Vec3(1.0, 1.0, 0.0); player.move(MoverType.SELF, Vec3(.5, 0.0, 0.0))
                        record(t, "void-client-prediction-locked", VoidStatus.isPhased(player) && player.position() == position && player.deltaMovement == Vec3.ZERO, "phased=${VoidStatus.isPhased(player)}")
                    }
                }
            }, finish = { ctx -> ctx.player.removeEffect(ModEffects.VOID_PHASE); WandCastingFx.clear() },
            checksAt = { _, t -> if (t == 179) checks.toSortedMap().values.flatten() else emptyList() })
    }
}
