package io.github.firestormfmd.scenescripter.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import io.github.firestormfmd.scenescripter.client.SceneScripterClient;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;

/**
 * Whether the scene editor is open on this client.
 */
public final class EditorMode {
	private static boolean open;

	private EditorMode() {
	}

	public static boolean isOpen() {
		return open;
	}

	public static void toggle(Minecraft client) {
		if (open) {
			close();
		} else {
			open(client);
		}
	}

	public static void open(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (!ClientNet.available()) {
			client.player.sendOverlayMessage(Component.translatable("scenescripter.editor.no_server"));
			return;
		}
		open = true;
		ClientNet.editorState(true);
		client.gui.setScreen(new EditorScreen());
	}

	public static void close() {
		if (!open) {
			return;
		}
		open = false;
		EditorState.pathDraft.clear();
		EditorState.dragPreview = null;
		Gizmo.cancel();
		if (ClientNet.available()) {
			ClientNet.editorState(false);
		}
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof EditorScreen) {
			client.gui.setScreen(null);
		}
	}

	/** Closes the editor without telling the server, such as when leaving a world. */
	public static void reset() {
		open = false;
	}

	public static boolean isToggleKey(int key) {
		return KeyMappingHelper.getBoundKeyOf(SceneScripterClient.TOGGLE_EDITOR).getValue() == key;
	}
}
