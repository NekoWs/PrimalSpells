package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.primalspells.client.render.AimHighlights
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.modifier.Aim
import work.nekow.primalspells.network.AimTargetPayload
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** 真正物品驱动被动、方块截断、首实体切换、原生轮廓与PD有限退场。 */
object AimScene {
    fun scene(): Scene {
        val name = "aim"
        var floor = 0
        lateinit var caster: TestDummyEntity
        lateinit var near: TestDummyEntity
        lateinit var far: TestDummyEntity
        lateinit var wand: Wand
        lateinit var stack: ItemStack
        lateinit var passive: Aim
        val checks = ConcurrentHashMap<Int, CopyOnWriteArrayList<Check>>()
        val scales = ConcurrentHashMap<Int, Float>()
        var sampleId: java.util.UUID? = null
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.computeIfAbsent(t) { CopyOnWriteArrayList() }.add(Check(name, t, id, ok, detail))
        }
        fun wall(ctx: CaptureDriver.SceneCtx, solid: Boolean) {
            for (x in -5..-3) for (y in floor + 1..floor + 5)
                ctx.level.setBlock(BlockPos(x, y, 0), (if (solid) Blocks.STONE else Blocks.AIR).defaultBlockState(), 2)
        }
        return Scene(name, "被动瞄准 / 墙面截断 / 原生目标轮廓", 24, 160, fov = 35,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor); ctx.setup(true, -2.0, floor + 2.8, 0.0, 34f, 34f)
                caster = ctx.spawnDummy(-4.0, floor + 1.0, -6.0)
                near = ctx.spawnDummy(-4.0, floor + 1.0, 3.0)
                far = ctx.spawnDummy(-4.0, floor + 1.0, 8.0)
                stack = ItemStack(ModItems.WAND.get())
                wand = WandManager[WandManager.createWand(stack)]
                wand.magics += MagicManager.create("aim"); wand.magics += MagicManager.create("aim"); wand.load()
                passive = wand.loadedMagics.first { it.id == "aim" } as Aim
            }, camera = { _, _ -> Pose(-2.0, floor + 2.8, 0.0, 34f, 34f) },
            tick = { ctx, t ->
                when (t) {
                    8 -> caster.setItemInHand(InteractionHand.MAIN_HAND, stack)
                    14 -> { sampleId = passive.sampleId
                        record(t, "real-held-wand-hits-nearest-with-one-beam", passive.lastHit?.entity === near && passive.particleCount == 4097,
                            "target=${passive.lastHit?.entity?.id} particles=${passive.particleCount}") }
                    35 -> wall(ctx, true)
                    41 -> record(t, "wall-clips-laser-and-prevents-through-wall-target", passive.lastHit?.entity == null &&
                        passive.lastHit?.point?.z?.let { kotlin.math.abs(it) < 0.001 } == true, "sight=${passive.lastHit}")
                    50 -> wall(ctx, false)
                    56 -> record(t, "target-reacquired-after-wall-removed", passive.lastHit?.entity === near, "sight=${passive.lastHit}")
                    65 -> near.setPos(-1.0, near.y, near.z)
                    71 -> record(t, "line-switches-to-next-visible-entity", passive.lastHit?.entity === far, "sight=${passive.lastHit}")
                    80 -> caster.setYRot(90f)
                    85 -> record(t, "beam-follows-caster-turn", passive.lastHit?.entity == null && (passive.lastHit?.point?.x ?: 0.0) < -14.9,
                        "sight=${passive.lastHit}")
                    90 -> caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                    99 -> record(t, "putting-wand-away-releases-server-session", passive.lastHit == null && passive.particleCount == 0,
                        "sight=${passive.lastHit} count=${passive.particleCount}")
                    105 -> far.setGlowingTag(true)
                    110 -> far.setGlowingTag(false)
                    115 -> { caster.setYRot(0f); caster.setItemInHand(InteractionHand.MAIN_HAND, stack) }
                    120 -> { wand.magics.clear(); wand.load() }
                    129 -> record(t, "card-removal-cleans-particles-and-state", passive.lastHit == null &&
                        ParticleManager.of(ctx.level).getEngine().particleCount() == 0,
                        "sight=${passive.lastHit} particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                }
            }, clientTick = { mc, t ->
                val clientNear = mc.level?.getEntity(near.id)
                val clientFar = mc.level?.getEntity(far.id)
                fun outlined(entity: net.minecraft.world.entity.Entity?) = entity?.let(mc::shouldEntityAppearGlowing) == true
                when (t) {
                    18 -> record(t, "native-outline-without-mutating-glowing-state", outlined(clientNear) && !outlined(clientFar) &&
                        clientNear?.isCurrentlyGlowing == false, "near=${outlined(clientNear)} far=${outlined(clientFar)}")
                    44 -> record(t, "occlusion-clears-client-outline", !outlined(clientNear) && !outlined(clientFar), "near=${outlined(clientNear)}")
                    75 -> record(t, "client-outline-switches-target", !outlined(clientNear) && outlined(clientFar), "far=${outlined(clientFar)}")
                    100 -> {
                        AimHighlights.accept(AimTargetPayload(caster.uuid, "lease-a", far.uuid))
                        AimHighlights.accept(AimTargetPayload(caster.uuid, "lease-b", far.uuid))
                        AimHighlights.accept(AimTargetPayload(caster.uuid, "lease-a", null))
                        record(t, "multiple-aim-leases-do-not-cancel-each-other", outlined(clientFar), "far=${outlined(clientFar)}")
                        AimHighlights.accept(AimTargetPayload(caster.uuid, "lease-b", null))
                    }
                    108 -> record(t, "original-glowing-is-preserved", clientFar?.isCurrentlyGlowing == true && outlined(clientFar), "glowing=${clientFar?.isCurrentlyGlowing}")
                    131 -> record(t, "client-outlines-cleaned-after-card-removal", !outlined(clientNear) && !outlined(clientFar), "far=${outlined(clientFar)}")
                }
                if (t in 89..102) scales[t] = sampleId?.let { ClientParticleEngine.instance()?.snapshot(it)?.scale } ?: 0f
            }, checksAt = { ctx, t ->
                if (t == 159) {
                    record(t, "native-beam-retires-through-intermediate-sizes", (scales[89] ?: 0f) > 0f && scales.filterKeys { it in 91..96 }.values.any { it > (scales[89] ?: 0f) * 0.05f && it < (scales[89] ?: 0f) * 0.95f } && scales[102] == 0f, "sizes=$scales")
                    record(t, "all-aim-particles-cleaned", ParticleManager.of(ctx.level).getEngine().particleCount() == 0,
                        "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                }
                checks[t].orEmpty()
            }, finish = { _ -> caster.discard(); near.discard(); far.discard(); wand.unload(); AimHighlights.clear() })
    }
}
