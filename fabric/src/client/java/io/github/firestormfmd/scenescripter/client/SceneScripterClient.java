package io.github.firestormfmd.scenescripter.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.client.editor.EditorMode;

public class SceneScripterClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SceneScripter.id("main"));

	/** Opens and closes the scene editor. Right Ctrl by default; rebindable in Controls. */
	public static final KeyMapping TOGGLE_EDITOR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.scenescripter.toggle_editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				// Left the world; the editor never stays open across worlds.
				EditorMode.reset();
				return;
			}
			while (TOGGLE_EDITOR.consumeClick()) {
				EditorMode.toggle(client);
			}
		});
	}
}
