package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.wand.Wand
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/** 持杖的展开、待机和冷却接线；逐帧造型与实体插值全部由PD表达式负责。 */
object WandCastingFx {
    const val EQUIP_TICKS = 16
    private val states = hashMapOf<UUID, Held>()

    private class Held(val player: ServerPlayer, val wand: Wand) {
        val level = player.level()
        val started = level.gameTime
        val groups = ArrayList<ParticleGroup>()
        var lastDelay = -1.0
        var lastRecharge = -1.0
        var lastEyeHeight = -1.0
        var lastBusy = ""
        var lastSide = 0.0
        val ready: Boolean get() = level.gameTime - started >= EQUIP_TICKS

        init {
            // 所有几何从实体脚底建立，表达式输出世界坐标；不与followEntity争夺定位权。
            group(96, 0.0625f, """
                lane=floor(3*i/n);q=3*i/n-lane;
                theta=PI/2+yaw+lane*2*PI/3;
                ax=cos(theta)*1.2;az=sin(theta)*1.2;
                bx=cos(theta+2*PI/3)*1.2;bz=sin(theta+2*PI/3)*1.2;
                x=fx+ax+(bx-ax)*q;y=fy;z=fz+az+(bz-az)*q;
                sc=min(1,max(0,(t/600-q)*24));r=0.26;g=0.78;b=0.86;a=1;
            """)
            group(36, 0.046875f, """
                lane=floor(3*i/n);theta=PI/2+yaw+lane*2*PI/3;
                phase=t/900+i/n*5;u=phase-floor(phase);
                sx=fx+cos(theta)*1.2;sz=fz+sin(theta)*1.2;
                curve=sin(PI*u)*0.22;
                x=sx+(hx-sx)*u+cos(theta+PI/2)*curve;
                y=fy+(hy-fy)*u+sin(PI*u)*0.16;
                z=sz+(hz-sz)*u+sin(theta+PI/2)*curve;
                sc=min(1,max(0,(t-600)/200))*(0.4+u*0.45)*(1-0.55*busy);
                r=0.55;g=0.93;b=0.89;a=1;
            """)
            group(60, 0.046875f, """
                lane=floor(3*i/n);q=3*i/n-lane;theta=PI/2+yaw+lane*2*PI/3;
                ax=cos(theta)*1.29;az=sin(theta)*1.29;
                bx=cos(theta+2*PI/3)*1.29;bz=sin(theta+2*PI/3)*1.29;
                x=fx+ax+(bx-ax)*q;y=fy+0.015;z=fz+az+(bz-az)*q;
                sc=min(1,max(0,(dp-q)*18))*delayOn;
                r=0.92;g=0.96;b=1;a=1;
            """)
            group(60, 0.0625f, """
                lane=floor(3*i/n);q=3*i/n-lane;theta=PI/2+yaw+lane*2*PI/3;
                rad=(1.08-0.7*rp)*(0.45+q*0.55);
                x=fx+cos(theta)*rad;y=fy+0.025;z=fz+sin(theta)*rad;
                sc=rechargeOn*min(1,max(0,(t-600)/150));
                r=0.96;g=0.66;b=0.25;a=1;
            """)
            group(1, 0.0234375f, """
                x=hx;y=hy;z=hz;
                sc=min(1,max(0,(t-600)/200));r=0.72;g=1;b=0.95;a=1;
            """)
            update()
        }

        private fun group(count: Int, size: Float, code: String) {
            val manager = ParticleManager.of(level)
            val group = manager.createGroup(player.position())
            repeat(count) {
                group.add(manager.create().position(player.position()).color(Color.ofInt(85, 218, 226))
                    .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(size, size).glowing(true).additive(false))
                    .lifetime(-1).spawn())
            }
            group.defineEntity("holder", player.uuid)
                .setVariable("eh", player.eyeHeight.toDouble())
                .setVariable("side", handOffset(player, wand))
                .setVariable("dp", 0.0).setVariable("rp", 0.0)
                .setVariable("busy", 0.0).setVariable("delayOn", 0.0).setVariable("rechargeOn", 0.0)
            group.expression("""
                fx=get_entity_x(holder);fy=get_entity_y(holder)+0.06;fz=get_entity_z(holder);
                yaw=get_entity_yaw(holder)*PI/180;
                hx=fx+get_entity_dirx(holder)*0.6+cos(yaw)*side;
                hy=fy-0.06+eh-0.12+get_entity_diry(holder)*0.6;
                hz=fz+get_entity_dirz(holder)*0.6+sin(yaw)*side;
                $code
            """.trimIndent())
            groups += group
        }

        fun update() {
            val delay = wand.status.delay.coerceAtLeast(0.0)
            val recharge = wand.status.recharge.coerceAtLeast(0.0)
            if (lastDelay != delay) {
                val progress = if (delay > 0) 1 - delay / wand.status.lastDelay.coerceAtLeast(delay) else 0.0
                groups.forEach { it.setVariableLive("dp", progress.toString()); it.setVariableLive("delayOn", if (delay > 0) "1" else "0") }
                lastDelay = delay
            }
            if (lastRecharge != recharge) {
                val progress = if (recharge > 0) 1 - recharge / wand.status.lastRecharge.coerceAtLeast(recharge) else 0.0
                groups.forEach { it.setVariableLive("rp", progress.toString()); it.setVariableLive("rechargeOn", if (recharge > 0) "1" else "0") }
                lastRecharge = recharge
            }
            val busy = if (delay > 0 || recharge > 0) "1" else "0"
            if (lastBusy != busy) {
                groups.forEach { it.setVariableLive("busy", busy) }
                lastBusy = busy
            }
            val eyeHeight = player.eyeHeight.toDouble()
            val side = handOffset(player, wand)
            if (lastSide != side) {
                groups.forEach { it.setVariableLive("side", side.toString()) }
                lastSide = side
            }
            if (eyeHeight != lastEyeHeight) {
                groups.forEach { it.setVariableLive("eh", eyeHeight.toString()) }
                lastEyeHeight = eyeHeight
            }
        }

        fun remove() = groups.forEach { it.remove() }
    }

    /** 使用中的手优先，否则主手优先；双持只显示一座法阵。 */
    private fun selected(player: ServerPlayer): Wand? {
        val stacks = if (player.isUsingItem) listOf(player.useItem, player.mainHandItem, player.offhandItem)
            else listOf(player.mainHandItem, player.offhandItem)
        return stacks.firstOrNull { it.item is WandItem }?.let { WandItem.getWand(it) }
    }

    private fun handOffset(player: ServerPlayer, wand: Wand): Double {
        val main = player.mainHandItem.item is WandItem && WandItem.getWand(player.mainHandItem) === wand
        val usingOffhand = player.isUsingItem && player.usedItemHand == net.minecraft.world.InteractionHand.OFF_HAND
        val side = if (player.mainArm == HumanoidArm.RIGHT) -0.22 else 0.22
        return if (main && !usingOffhand) side else -side
    }

    private fun state(player: ServerPlayer, wand: Wand): Held {
        val current = states[player.uuid]
        if (current != null && current.wand === wand && current.player === player && current.level === player.level()) return current
        current?.remove()
        return Held(player, wand).also { states[player.uuid] = it }
    }

    /** 每服务器tick维护持杖者；死亡、换维度、切杖、放下、登出立即清理旧组。 */
    fun tick(server: MinecraftServer) {
        val held = hashSetOf<UUID>()
        for (player in server.playerList.players) {
            val wand = if (player.isAlive) selected(player) else null
            if (wand == null) continue
            held += player.uuid
            state(player, wand).update()
        }
        val iterator = states.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in held) { entry.value.remove(); iterator.remove() }
        }
    }

    /** 只限制真正拿在手里的玩家法杖；AI与直接测试配方不受装备动画阻塞。 */
    fun ready(caster: Entity, wand: Wand): Boolean {
        val player = caster as? ServerPlayer ?: return true
        if (selected(player) !== wand) return true
        return state(player, wand).ready
    }

    /** 汇聚终点与真实投射物出生点共用同一位置，保留非持杖施法的原入口。 */
    fun origin(caster: Entity, wand: Wand): Vec3 {
        val player = caster as? ServerPlayer
        if (player == null || selected(player) !== wand) return caster.eyePosition.add(0.0, -0.1, 0.0)
        val yaw = Math.toRadians(player.yRot.toDouble())
        val side = handOffset(player, wand)
        return caster.eyePosition.add(0.0, -0.12, 0.0).add(caster.lookAngle.scale(0.6))
            .add(cos(yaw) * side, 0.0, sin(yaw) * side)
    }

    fun forget(playerId: UUID) { states.remove(playerId)?.remove() }
    fun clear() { states.values.forEach { it.remove() }; states.clear() }
}
