package io.github.firestormfmd.scenescripter;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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

		// Players cannot hit or use actors, on either side.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				Actors.isActor(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
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
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
