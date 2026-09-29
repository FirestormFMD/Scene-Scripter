package io.github.firestormfmd.scenescripter.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import io.github.firestormfmd.scenescripter.server.SceneManager;

/**
 * Records arm swings of players performing an object; vanilla has no event for them. From 26.3 the client reports
 * only main-hand swings, as punches.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At("TAIL"))
	private void scenescripter$recordSwing(ServerboundPunchPacket packet, CallbackInfo ci) {
		SceneManager.get().ifPresent(m -> m.onCaptureEvent(player, "swing", null, Map.of("hand", "main")));
	}
}
