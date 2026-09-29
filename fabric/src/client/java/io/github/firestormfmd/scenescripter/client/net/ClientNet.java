package io.github.firestormfmd.scenescripter.client.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.firestormfmd.scenescripter.core.edit.EditOp;
import io.github.firestormfmd.scenescripter.core.io.EditOpCodec;
import io.github.firestormfmd.scenescripter.net.Chunks;
import io.github.firestormfmd.scenescripter.net.Payloads;

/**
 * Sends editor requests to the server. The server applies them and sends back the updated scene.
 */
public final class ClientNet {
	private ClientNet() {
	}

	public static boolean available() {
		return ClientPlayNetworking.canSend(Payloads.Edit.TYPE);
	}

	public static void edit(EditOp op) {
		for (Chunks.Part part : Chunks.split(EditOpCodec.write(op))) {
			ClientPlayNetworking.send(new Payloads.Edit(part));
		}
	}

	public static void undo() {
		ClientPlayNetworking.send(new Payloads.History(false));
	}

	public static void redo() {
		ClientPlayNetworking.send(new Payloads.History(true));
	}

	public static void play() {
		ClientPlayNetworking.send(new Payloads.Playback(Payloads.Playback.PLAY, 0, 0, 1));
	}

	public static void pause() {
		ClientPlayNetworking.send(new Payloads.Playback(Payloads.Playback.PAUSE, 0, 0, 1));
	}

	public static void stop() {
		ClientPlayNetworking.send(new Payloads.Playback(Payloads.Playback.STOP, 0, 0, 1));
	}

	public static void seek(int tick) {
		ClientPlayNetworking.send(new Payloads.Playback(Payloads.Playback.SEEK, tick, 0, 1));
	}

	public static void speed(float speed) {
		ClientPlayNetworking.send(new Payloads.Playback(Payloads.Playback.SPEED, 0, 0, speed));
	}

	public static void loop(int start, int end) {
		ClientPlayNetworking.send(new Payloads.Playback(Payloads.Playback.LOOP, start, end, 1));
	}

	public static void editorState(boolean open) {
		ClientPlayNetworking.send(new Payloads.EditorState(open));
	}

	public static void sceneCommand(int action, String name, int length) {
		ClientPlayNetworking.send(new Payloads.SceneCommand(action, name, length));
	}

	/** Starts performing an object. {@code punchOut} is -1 to record to the end of the scene. */
	public static void startCapture(String objectId, int punchIn, int punchOut, int preroll, boolean loop) {
		ClientPlayNetworking.send(new Payloads.Capture(Payloads.Capture.START, objectId, punchIn, punchOut, preroll, loop, "", ""));
	}

	public static void stopCapture() {
		ClientPlayNetworking.send(new Payloads.Capture(Payloads.Capture.STOP, "", 0, -1, 0, false, "", ""));
	}

	/** Applies a take: {@code keys}, {@code raw} or {@code path}. */
	public static void useTake(String takeId, String mode) {
		ClientPlayNetworking.send(new Payloads.Capture(Payloads.Capture.USE_TAKE, "", 0, -1, 0, false, takeId, mode));
	}
}
