package io.github.firestormfmd.scenescripter.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Whether the scene editor is open on this client. The editor UI, tools and camera hang off this state.
 */
public final class EditorMode {
	private static boolean open;

	private EditorMode() {
	}

	public static boolean isOpen() {
		return open;
	}

	public static void toggle(Minecraft client) {
		open = !open;
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.translatable(
					open ? "scenescripter.editor.opened" : "scenescripter.editor.closed"));
		}
	}

	/** Closes the editor without a message, such as when leaving a world. */
	public static void reset() {
		open = false;
	}
}
