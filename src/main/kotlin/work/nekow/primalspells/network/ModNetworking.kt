package work.nekow.primalspells.network

import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/** 公共协议接线；客户端回调经sink分发，服务端请求统一交给权威校验入口。 */
object ModNetworking {
    const val PROTOCOL_VERSION = "1.7.0"

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)
        registrar.playToClient(SyncWandStatsPayload.ID, SyncWandStatsPayload.STREAM_CODEC) { payload, _ ->
            ClientPayloadSink.wandStats?.invoke(payload)
        }
        registrar.playToClient(WandCastPayload.ID, WandCastPayload.STREAM_CODEC) { payload, _ ->
            ClientPayloadSink.wandCast?.invoke(payload)
        }
        registrar.playToClient(HudImpactPayload.ID, HudImpactPayload.STREAM_CODEC) { payload, _ ->
            ClientPayloadSink.hudImpact?.invoke(payload)
        }
        registrar.playToClient(ScreenShakePayload.ID, ScreenShakePayload.STREAM_CODEC) { payload, _ ->
            ClientPayloadSink.screenShake?.invoke(payload)
        }
        registrar.playBidirectional(WandPreviewPayload.ID, WandPreviewPayload.STREAM_CODEC) { payload, context ->
            val player = context.player()
            if (player is ServerPlayer) WandRequestHandler.preview(player, payload)
            else ClientPayloadSink.wandPreview?.invoke(payload)
        }
        registrar.playToServer(EditWandPayload.ID, EditWandPayload.STREAM_CODEC) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer
            WandRequestHandler.edit(player, payload)
        }
        registrar.playToServer(SelectSpellSlotPayload.ID, SelectSpellSlotPayload.STREAM_CODEC) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer
            WandRequestHandler.select(player, payload)
        }
    }
}
