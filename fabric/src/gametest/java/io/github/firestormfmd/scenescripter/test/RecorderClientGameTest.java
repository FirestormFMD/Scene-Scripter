package io.github.firestormfmd.scenescripter.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Runs only beside a recorder (CI adds Flashback or Replay Mod with {@code -Precorder=...}): plays the tutorial
 * scene and opens and closes the editor with the recorder loaded. Replay Mod records singleplayer by itself, so
 * with it the test also checks that the recording was saved and holds packets.
 */
@SuppressWarnings("UnstableApiUsage")
public class RecorderClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		boolean replayMod = FabricLoader.getInstance().isModLoaded("replaymod");
		boolean flashback = FabricLoader.getInstance().isModLoaded("flashback");
		if (!replayMod && !flashback) {
			return;
		}
		String recorder = replayMod ? "replaymod" : "flashback";
		Path recordings = FabricLoader.getInstance().getGameDir().resolve("replay_recordings");
		List<Path> before = files(recordings);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode creative @a");
			singleplayer.getServer().runCommand("scene tutorial recorded");
			singleplayer.getServer().runCommand("scene play");
			context.waitTicks(100);
			Screenshots.printThumbnail(context.takeScreenshot("scenescripter-with-" + recorder), "with-" + recorder);

			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_CONTROL);
			context.waitFor(client -> client.gui.screen() != null
					&& client.gui.screen().getClass().getSimpleName().equals("EditorScreen"), 100);
			context.waitTicks(5);
			Screenshots.printThumbnail(context.takeScreenshot("scenescripter-editor-with-" + recorder), "editor-with-" + recorder);
			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_CONTROL);
			context.waitFor(client -> client.gui.screen() == null, 100);
			singleplayer.getServer().runCommand("scene play");
			context.waitTicks(100);
			System.out.println("Recordings folder while in the world: " + files(recordings).stream()
					.map(p -> recordings.relativize(p) + " " + size(p)).toList());
		}
		if (replayMod) {
			// Replay Mod writes the recording while the world is open and finishes it after leaving; depending on its
			// settings the finished file lands in the folder itself or waits in raw/. Either counts, once it holds
			// a good amount of recorded packets.
			Path found = null;
			for (int wait = 0; wait < 12 && found == null; wait++) {
				context.waitTicks(200);
				String screen = context.computeOnClient(client -> client.gui.screen() == null ? "none"
						: client.gui.screen().getClass().getName());
				List<Path> now = files(recordings);
				System.out.println("Replay Mod recordings after " + (wait + 1) * 10 + " s (screen " + screen + "):");
				for (Path p : now) {
					System.out.println("  " + recordings.relativize(p) + " " + size(p) + " bytes");
				}
				found = now.stream().filter(p -> !before.contains(p) && size(p) > 10_000).findFirst().orElse(null);
			}
			if (found == null) {
				throw new AssertionError("Replay Mod saved no recording under " + recordings);
			}
			if (found.getFileName().toString().endsWith(".mcpr")) {
				try (ZipFile zip = new ZipFile(found.toFile())) {
					var packets = zip.getEntry("recording.tmcpr");
					System.out.println("Replay Mod recording " + found.getFileName() + " holds "
							+ (packets == null ? "no packet stream" : packets.getSize() + " bytes of packets"));
					if (packets == null || packets.getSize() < 10_000) {
						throw new AssertionError("The Replay Mod recording holds no packets");
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		}
	}

	/** Every file under a folder, at any depth. */
	private static List<Path> files(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.walk(dir)) {
			return s.filter(Files::isRegularFile).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static long size(Path p) {
		try {
			return Files.size(p);
		} catch (IOException e) {
			return 0;
		}
	}
}
