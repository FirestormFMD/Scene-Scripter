package io.github.firestormfmd.scenescripter.client;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.loader.api.FabricLoader;

import io.github.firestormfmd.scenescripter.SceneScripter;

/**
 * Starts and finishes a Flashback recording along with Record, when Flashback is installed. Flashback has no API
 * for other mods, so its public static methods behind its own Start and Finish Recording keys are called by
 * reflection; when they can't be reached, the player starts the recording by hand as before. A recording the
 * player started themselves is left alone.
 */
public final class FlashbackRecording {
	/** How long the last frame stays in the recording after the scene stops playing, in client ticks. */
	private static final int TAIL_TICKS = 20;

	/** Whether the current Flashback recording was started by Record, and so is finished by it too. */
	private static boolean ours;
	/** Ticks left before finishing, once the scene has stopped playing; -1 while it plays. */
	private static int finishIn = -1;

	private FlashbackRecording() {
	}

	/** The server says the scene started ({@code active}) or stopped playing for the recorder. */
	public static void onRecordingRun(boolean active) {
		if (!FabricLoader.getInstance().isModLoaded("flashback")) {
			return;
		}
		if (active) {
			finishIn = -1;
			if (!ours && !recording()) {
				ours = call("startRecordingReplay") && recording();
				if (!ours) {
					message("Could not start Flashback; start its recording yourself");
				}
			}
		} else if (ours) {
			finishIn = TAIL_TICKS;
		}
	}

	/** Each client tick: finishes a recording Record started, a moment after the scene stops playing. */
	public static void tick() {
		if (!ours) {
			return;
		}
		if (!recording()) {
			// Finished or cancelled in Flashback itself.
			ours = false;
			finishIn = -1;
			return;
		}
		if (finishIn > 0 && --finishIn == 0) {
			ours = false;
			finishIn = -1;
			if (!call("finishRecordingReplay")) {
				message("Could not finish the Flashback recording; finish it yourself");
			}
		}
	}

	/** Leaving the world: Flashback finishes its recording by itself then. */
	public static void reset() {
		ours = false;
		finishIn = -1;
	}

	private static boolean recording() {
		try {
			return flashback().getField("RECORDER").get(null) != null;
		} catch (ReflectiveOperationException | LinkageError e) {
			return false;
		}
	}

	private static boolean call(String method) {
		try {
			Method m = flashback().getMethod(method);
			m.invoke(null);
			return true;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			SceneScripter.LOGGER.warn("Could not call Flashback's {}", method, e);
			return false;
		}
	}

	private static Class<?> flashback() throws ClassNotFoundException {
		return Class.forName("com.moulberry.flashback.Flashback");
	}

	private static void message(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal(text));
		}
	}
}
