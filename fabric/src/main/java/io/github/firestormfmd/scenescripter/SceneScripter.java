package io.github.firestormfmd.scenescripter;

import java.util.Map;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.firestormfmd.scenescripter.actor.Actors;
import io.github.firestormfmd.scenescripter.command.SceneCommands;
import io.github.firestormfmd.scenescripter.net.Payloads;
import io.github.firestormfmd.scenescripter.server.SceneManager;

public class SceneScripter implements ModInitializer {
	public static final String MOD_ID = "scenescripter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Payloads.register();

		ServerLifecycleEvents.SERVER_STARTED.register(SceneManager::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SceneManager.stop());
		ServerTickEvents.END_SERVER_TICK.register(server -> SceneManager.get().ifPresent(SceneManager::tick));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				SceneManager.get().ifPresent(m -> m.onJoin(handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				SceneManager.get().ifPresent(m -> m.onLeave(handler.player)));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SceneCommands.register(dispatcher));

		// Players cannot hit or use actors, on either side. A performer's hits are recorded as attacks instead.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!Actors.isActor(entity)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && Actors.objectId(entity) != null) {
				SceneManager.get().ifPresent(m -> m.onCaptureEvent(sp, "attack", Actors.objectId(entity), Map.of("hand", "main")));
			}
			return InteractionResult.FAIL;
		});
		// While performing, block interactions are recorded as scene events instead of changing the world.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && SceneManager.get().map(m -> m.isCapturing(sp)).orElse(false)) {
				SceneManager.get().get().onCaptureEvent(sp, "break_block", null,
						Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
				return false;
			}
			return true;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND
					|| !SceneManager.get().map(m -> m.isCapturing(sp)).orElse(false)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof BlockItem blockItem) {
				BlockPlaceContext ctx = new BlockPlaceContext(player, hand, held, hit);
				BlockState placed = blockItem.getBlock().getStateForPlacement(ctx);
				if (placed != null) {
					BlockPos at = ctx.getClickedPos();
					SceneManager.get().get().onCaptureEvent(sp, "place_block", null, Map.of("x", at.getX(), "y", at.getY(),
							"z", at.getZ(), "block", BlockStateParser.serialize(placed)));
				}
			} else {
				BlockPos at = hit.getBlockPos();
				SceneManager.get().get().onCaptureEvent(sp, "use_block", null, Map.of("x", at.getX(), "y", at.getY(),
						"z", at.getZ()));
			}
			return InteractionResult.FAIL;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				Actors.isActor(entity) ? InteractionResult.FAIL : InteractionResult.PASS);

		registerReceivers();
		LOGGER.info("Scene Scripter loaded");
	}

	private static void registerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(Payloads.EditorState.TYPE, (payload, context) ->
				SceneManager.get().ifPresent(m -> m.setEditorOpen(context.player(), payload.open())));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Edit.TYPE, (payload, context) ->
				SceneManager.get().ifPresent(m -> m.onEditPart(context.player(), payload.part())));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.History.TYPE, (payload, context) ->
				SceneManager.get().ifPresent(m -> m.onHistory(context.player(), payload.redo())));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Playback.TYPE, (payload, context) ->
				SceneManager.get().ifPresent(m -> m.onPlayback(payload)));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SceneCommand.TYPE, (payload, context) ->
				SceneManager.get().ifPresent(m -> m.onSceneCommand(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.EditorView.TYPE, (payload, context) ->
				SceneManager.get().ifPresent(m -> m.onEditorView(context.player(), payload.hidden())));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Capture.TYPE, (payload, context) ->
				SceneManager.get().ifPresent(m -> m.onCapture(context.player(), payload)));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
