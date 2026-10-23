package work.nekow.primalspells.magic.effect.visual

import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.util.spellParticle
import work.nekow.primalspells.util.spellVisual
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandGrip

/** 持杖法阵、常驻符印和宝石施法；逐帧轨迹、退场及成员寿命交给PD。 */
object WandCastingFx {
    const val EQUIP_TICKS = 8
    private val states = hashMapOf<UUID, Held>()
    // 只保留有限退场会话，便于登出/跨维度立即取消；粒子成员和销毁调度仍由PD管理。
    private val retiring = ArrayList<Held>()

    private class Held(val player: ServerPlayer, val wand: Wand) {
        val level = player.level()
        val started = level.gameTime
        val groups = ArrayList<ParticleGroup>()
        var lastDelay = -1.0
        var lastRecharge = -1.0
        private var delayFx: ParticleGroup? = null
        private var rechargeFx: ParticleGroup? = null
        val ready: Boolean get() = level.gameTime - started >= EQUIP_TICKS
        private val elapsed: Double get() = (level.gameTime - started) * 50.0

        init {
            group(72, 0.09375f, """
                lane=floor(3*i/n);q=3*i/n-lane;
                theta=PI/2+lane*2*PI/3+t*0.00015;
                ax=cos(theta)*1.2;az=sin(theta)*1.2;
                bx=cos(theta+2*PI/3)*1.2;bz=sin(theta+2*PI/3)*1.2;
                scatter=sin(i*91.71)*0.5+0.5;
                dissolve=fade;
                x=fx+ax+(bx-ax)*q;y=fy+dissolve*dissolve*0.08;
                z=fz+az+(bz-az)*q;
                sc=min(1,max(0,(t/260-q)*24))*pow(remaining,0.75);
                glint=pow(max(0,cos(2*PI*q-t*0.0018-lane*2*PI/3)),12);
                r=0.20+glint*0.30;g=0.65+glint*0.30;b=0.78+glint*0.22;a=1;
            """)
            // 角点锁印沿地面缓慢呼吸；杖首idle由原生模型模块承担，避免眼锚误充手锚。
            group(24, 0.09375f, """
                lane=floor(i/8);node=i-lane*8;angle=node*PI/4;
                theta=PI/2+lane*2*PI/3+t*0.00015;
                rad=0.15+0.015*sin(t*0.0016+lane*2*PI/3);
                x=fx+cos(theta)*1.2+cos(angle)*rad;
                y=fy+0.025;z=fz+sin(theta)*1.2+sin(angle)*rad;
                entry=min(1,max(0,(t-180)/160));entry=entry*entry*(3-2*entry);
                sc=entry*(0.86+0.10*sin(t*0.0016+lane*2*PI/3))*pow(remaining,0.75);
                r=0.38;g=0.92;b=1;a=1;
            """)
            update()
        }

        /** 白色内边束印逐段回收：法杖正在恢复下一次释放能力。 */
        private fun newDelayFx(): ParticleGroup = group(72, 0.09375f, """
                lane=floor(3*i/n);q=3*i/n-lane;
                theta=PI/2+lane*2*PI/3+t*0.00015;
                ax=cos(theta)*1.07;az=sin(theta)*1.07;
                bx=cos(theta+2*PI/3)*1.07;bz=sin(theta+2*PI/3)*1.07;
                done=min(1,max(0,(t-delayEnd)/150));done=done*done*(3-2*done);
                appear=min(1,max(0,(t-delayStart)/75));appear=appear*appear*(3-2*appear);
                lit=min(1,max(0,(1-dp-q)*24+1));lit=lit*lit*(3-2*lit);
                x=fx+ax+(bx-ax)*q;y=fy+0.03;z=fz+az+(bz-az)*q;
                sc=delayOn*appear*(0.28+0.72*lit)*(1-done)*pow(remaining,0.75);
                r=0.88;g=0.96;b=1;a=1;
            """)

        /** 蓝色外边导槽由三顶点充满；充满后并回主法阵，表示牌堆已完成回充。 */
        private fun newRechargeFx(): ParticleGroup = group(72, 0.09375f, """
                lane=floor(3*i/n);q=3*i/n-lane;
                theta=PI/2+lane*2*PI/3+t*0.00015;
                done=min(1,max(0,(t-rechargeEnd)/175));done=done*done*(3-2*done);
                appear=min(1,max(0,(t-rechargeStart)/75));appear=appear*appear*(3-2*appear);
                rad=1.32-done*0.12;
                ax=cos(theta)*rad;az=sin(theta)*rad;
                bx=cos(theta+2*PI/3)*rad;bz=sin(theta+2*PI/3)*rad;
                x=fx+ax+(bx-ax)*q;y=fy+0.015;z=fz+az+(bz-az)*q;
                lit=min(1,max(0,(rp-q)*24+1));lit=lit*lit*(3-2*lit);
                sc=rechargeOn*appear*(0.45+lit*0.55)*(1-done)*pow(remaining,0.75);
                r=0.12+lit*0.16;g=0.30+lit*0.38;b=0.72+lit*0.28;a=1;
            """)

        private fun group(count: Int, size: Float, code: String): ParticleGroup {
            val manager = ParticleManager.of(level)
            // 出生包保持隐藏，表达式第一帧直接给出尺寸/颜色，避免包间露出完整图形。
            val group = manager.createGroup(player.position())
            repeat(count) {
                group.add(manager.spellParticle().position(player.position()).color(Color.ofInt(85, 218, 226).withAlpha(0f))
                    .visual(spellVisual().style(ParticleStyle.SQUARE).anisoWorld(size, size).additive(false))
                    .lifetime(-1).spawn())
            }
            group.setVariable("clockOffset", elapsed)
                .setVariable("seed", (player.uuid.hashCode() and 65535).toDouble())
                .setVariable("delayStart", -1.0e12).setVariable("rechargeStart", -1.0e12)
                .setVariable("delayDuration", 1.0).setVariable("rechargeDuration", 1.0)
                .setVariable("delayOn", 0.0).setVariable("rechargeOn", 0.0)
                .setVariable("presence", 1.0).setVariable("delayEnd", 1.0e12).setVariable("rechargeEnd", 1.0e12)
            group.defineEntity("holder", player.uuid).expression("""
                now=t+clockOffset;
                fx=get_entity_x(holder);fy=get_entity_y(holder)+0.06;fz=get_entity_z(holder);
                remaining=presence;fade=1-presence;
                dp=min(1,max(0,(now-delayStart)/delayDuration));
                rp=min(1,max(0,(now-rechargeStart)/rechargeDuration));
                ${code.replace(Regex("\\bt\\b"), "now")}
            """.trimIndent())
            groups += group
            return group
        }

        /** PD实体getter直接采样位置，原生粒子桥负责位置插值。 */
        fun followPosition() { }

        fun update() {
            followPosition()
            val delay = wand.status.delay.coerceAtLeast(0.0)
            val recharge = wand.status.recharge.coerceAtLeast(0.0)
            // 每轮状态有独立PD会话；重复施法时旧提示正常收尾，新提示从零展开。
            fun finish(group: ParticleGroup?, end: String) {
                group?.setVariableLive(end, elapsed.toString())
                group?.destroyAfter(7)
            }
            fun begin(group: ParticleGroup, prefix: String, duration: Double) {
                group.setVariableLive(prefix + "Start", elapsed.toString())
                group.setVariableLive(prefix + "Duration", (duration * 50.0).toString())
                group.setVariableLive(prefix + "On", "1")
            }
            if (delay > lastDelay && delay > 0) {
                if (lastDelay > 0) finish(delayFx, "delayEnd")
                delayFx = newDelayFx().also { begin(it, "delay", delay) }
            } else if (delay == 0.0 && lastDelay > 0) finish(delayFx, "delayEnd")
            if (recharge > lastRecharge && recharge > 0) {
                if (lastRecharge > 0) finish(rechargeFx, "rechargeEnd")
                rechargeFx = newRechargeFx().also { begin(it, "recharge", recharge) }
            } else if (recharge == 0.0 && lastRecharge > 0) finish(rechargeFx, "rechargeEnd")
            lastDelay = delay
            lastRecharge = recharge
            groups.removeAll { it.size() == 0 }

        }

        fun retire() {
            // PD1.0.20按客户端变量终点完成后回收；离线兜底由PD负责。
            groups.forEach { it.setVariableInterpolated("presence", "0", 8, EasingType.LINEAR).retire() }
            retiring += this
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
        val side = if (player.mainArm == HumanoidArm.RIGHT) -WandGrip.SIDE else WandGrip.SIDE
        return if (main && !usingOffhand) side else -side
    }

    private fun state(player: ServerPlayer, wand: Wand): Held {
        val current = states[player.uuid]
        if (current != null && current.wand === wand && current.player === player && current.level === player.level()) return current
        if (current?.player === player && current.level === player.level()) current.retire() else current?.remove()
        return Held(player, wand).also { states[player.uuid] = it }
    }

    /** 结算完成立即发布真实冷却状态，避免等待下一次会话tick。 */
    fun cooldownsChanged(caster: Entity, wand: Wand) {
        val player = caster as? ServerPlayer ?: return
        if (selected(player) === wand) state(player, wand).update()
    }

    /** 切杖/放下播放有限退场；死亡、维度变化、登出立即清理，避免追踪失效实体。 */
    fun tick(server: MinecraftServer) {
        retiring.removeAll {
            val invalid = !it.player.isAlive || it.player.level() !== it.level || server.playerList.getPlayer(it.player.uuid) !== it.player
            if (invalid) it.remove() else it.followPosition()
            invalid || it.groups.all { group -> group.size() == 0 }
        }
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
            if (entry.key !in held) {
                if (entry.value.player.isAlive && entry.value.player.level() === entry.value.level && server.playerList.getPlayer(entry.key) === entry.value.player)
                    entry.value.retire() else entry.value.remove()
                iterator.remove()
            }
        }
    }

    /** 只限制真正拿在手里的玩家法杖；AI与直接测试配方不受装备动画阻塞。 */
    fun ready(caster: Entity, wand: Wand): Boolean {
        val player = caster as? ServerPlayer ?: return true
        if (selected(player) !== wand) return true
        return state(player, wand).ready
    }

    /** HUD只读实际拿起会话；背包内其它法杖不创建表现会话。 */
    fun equipRemaining(player: ServerPlayer, wand: Wand): Int {
        if (!player.isAlive || selected(player) !== wand) return 0
        val held = state(player, wand)
        return (EQUIP_TICKS - (held.level.gameTime - held.started)).coerceIn(0, EQUIP_TICKS.toLong()).toInt()
    }

    /** 模型宝石位于视线右下方，施法和迸发共用同一相机正交基；副手/左惯用手镜像。 */
    fun origin(caster: Entity, wand: Wand): Vec3 {
        val player = caster as? ServerPlayer
        if (player == null || selected(player) !== wand) return caster.eyePosition.add(0.0, -0.1, 0.0)
        val yaw = Math.toRadians(player.yRot.toDouble())
        val forward = caster.lookAngle
        val up = Vec3(sin(yaw) * forward.y, kotlin.math.sqrt((1-forward.y*forward.y).coerceAtLeast(0.0)), -cos(yaw)*forward.y)
        val side = handOffset(player, wand)
        return caster.eyePosition.add(forward.scale(WandGrip.FORWARD)).add(up.scale(WandGrip.UP))
            .add(cos(yaw)*side, 0.0, sin(yaw)*side)
    }

    /** 成功释放投射物时的一次宝石迸发；多重施法只播放一次，不额外增加施法延迟。 */
    fun cast(caster: Entity, wand: Wand) {
        val player = caster as? ServerPlayer ?: return
        if (selected(player) !== wand) return
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, work.nekow.primalspells.network.WandCastPayload(wand.id))
        val center = origin(caster, wand)
        val forward = work.nekow.primalspells.wand.WandAim.direction(caster, center)
        val manager = ParticleManager.of(caster.level())
        val group = manager.createGroup(center)
        repeat(24) { i ->
            group.add(manager.spellParticle().position(center).color(Color.ofInt(195, 253, 240).withAlpha(0f))
                .visual(spellVisual().style(ParticleStyle.SQUARE).anisoWorld(if(i<6) 0.1875f else 0.125f, 0.125f)
                    .additive(false)).lifetime(-1).spawn())
        }
        group.setVariable("cx", center.x).setVariable("cy", center.y).setVariable("cz", center.z)
            .setVariable("dx", forward.x).setVariable("dy", forward.y).setVariable("dz", forward.z)
            .expression("""
                p=min(1,t/250);birth=min(1,t/60);birth=birth*birth*(3-2*birth);h=sin(i*127.1)*43758.5453;h=h-floor(h);
                angle=i*2.399963;spread=sin(PI*p)*(0.08+h*0.16);
                x=cx+dx*p*(0.25+h*0.45)+cos(angle)*spread;
                y=cy+dy*p*(0.25+h*0.45)+sin(angle)*spread;
                z=cz+dz*p*(0.25+h*0.45)+sin(angle*1.71)*spread;
                sc=birth*(1-p)*(1-p);r=0.73;g=1;b=0.92;a=1;
            """.trimIndent(), 5).retire()
    }

    /** 登出清理活动法阵；有限退场组的销毁已由PD调度。 */
    fun forget(playerId: UUID) {
        states.remove(playerId)?.remove()
        retiring.removeAll { if (it.player.uuid == playerId) { it.remove(); true } else false }
    }
    /** 世界加载/服务器停止时清除活动会话。 */
    fun clear() {
        states.values.forEach { it.remove() }; states.clear()
        retiring.forEach { it.remove() }; retiring.clear()
    }
}
