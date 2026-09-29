package io.github.firestormfmd.scenescripter.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.client.editor.EditorMode;
import io.github.firestormfmd.scenescripter.net.Payloads;

public class SceneScripterClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SceneScripter.id("main"));

	/** Opens and closes the scene editor. Right Ctrl by default; rebindable in Controls. */
	public static final KeyMapping TOGGLE_EDITOR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.scenescripter.toggle_editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				EditorMode.reset();
				return;
			}
			while (TOGGLE_EDITOR.consumeClick()) {
				if (ClientScene.capturing()) {
					// While performing, the editor key ends the take instead.
					io.github.firestormfmd.scenescripter.client.net.ClientNet.stopCapture();
				} else {
					EditorMode.toggle(client);
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(Payloads.SceneData.TYPE, (payload, context) ->
				ClientScene.onSceneData(payload.part()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.PlaybackState.TYPE, (payload, context) ->
				ClientScene.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.ActorIds.TYPE, (payload, context) ->
				ClientScene.onActorIds(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.SceneList.TYPE, (payload, context) ->
				ClientScene.onSceneList(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.CaptureState.TYPE, (payload, context) -> {
			ClientScene.onCaptureState(payload);
			if (payload.active()) {
				EditorMode.close();
			}
		});

		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> ClientScene.onEntityLoad(entity));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			EditorMode.reset();
			ClientScene.reset();
		});
	}
}
