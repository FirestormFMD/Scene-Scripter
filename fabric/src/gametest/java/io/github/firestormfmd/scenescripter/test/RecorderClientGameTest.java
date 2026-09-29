package io.github.firestormfmd.scenescripter.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Runs only beside a recorder (CI adds Flashback or Replay Mod with {@code -Precorder=...}): plays the tutorial
 * scene and opens and closes the editor with the recorder loaded, checks that the recorder saved a recording that
 * holds packets, and plays it back in the recorder's viewer to see the scene's actors in it. Replay Mod records
 * singleplayer by itself; Flashback is started and finished by Scene Scripter's Record.
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
		Path flashbackReplays = flashback ? (Path) flashback("getReplayFolder") : null;
		List<Path> flashbackBefore = flashback ? files(flashbackReplays) : List.of();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode creative @a");
			singleplayer.getServer().runCommand("scene tutorial recorded");
			if (flashback) {
				// Record, run as the player, starts Flashback's recording with the scene.
				context.waitTicks(5);
				context.runOnClient(client -> flashbackQuicksave());
				singleplayer.getServer().runCommand("execute as @p run scene record 20");
				context.waitFor(client -> flashbackRecording(), 100);
			} else {
				singleplayer.getServer().runCommand("scene play");
			}
			context.waitTicks(100);
			Screenshots.printThumbnail(context.takeScreenshot("scenescripter-with-" + recorder), "with-" + recorder);

			context.getInput().pressKey(InputConstants.KEY_RCONTROL);
			context.waitFor(client -> client.gui.screen() != null
					&& client.gui.screen().getClass().getSimpleName().equals("EditorScreen"), 100);
			context.waitTicks(5);
			Screenshots.printThumbnail(context.takeScreenshot("scenescripter-editor-with-" + recorder), "editor-with-" + recorder);
			context.getInput().pressKey(InputConstants.KEY_RCONTROL);
			context.waitFor(client -> client.gui.screen() == null, 100);
			if (flashback) {
				// And finishes it a moment after the scene has played through.
				context.waitFor(client -> !flashbackRecording(), 20 * 30);
				System.out.println("Record started and finished the Flashback recording");
			} else {
				singleplayer.getServer().runCommand("scene play");
				context.waitTicks(100);
			}
			System.out.println("Recordings folder while in the world: " + files(recordings).stream()
					.map(p -> recordings.relativize(p) + " " + size(p)).toList());
		}
		if (flashback) {
			Path found = files(flashbackReplays).stream()
					.filter(p -> !flashbackBefore.contains(p) && p.getFileName().toString().endsWith(".zip"))
					.max(java.util.Comparator.comparingLong(RecorderClientGameTest::modified)).orElse(null);
			if (found == null) {
				throw new AssertionError("Flashback saved no recording under " + flashbackReplays);
			}
			try (ZipFile zip = new ZipFile(found.toFile())) {
				long packets = zip.stream().filter(e -> e.getName().endsWith(".flashback")).mapToLong(ZipEntry::getSize).sum();
				System.out.println("Flashback recording " + found.getFileName() + " (" + size(found) + " bytes) holds "
						+ packets + " bytes of packets in " + zip.stream().map(ZipEntry::getName).toList());
				if (zip.getEntry("metadata.json") == null || packets < 10_000) {
					throw new AssertionError("The Flashback recording holds no packets");
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			playBackInFlashback(context, found);
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
				found = now.stream().filter(p -> !before.contains(p) && size(p) > 10_000)
						.max(java.util.Comparator.comparingLong(RecorderClientGameTest::modified)).orElse(null);
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
				playBack(context, found);
			}
		}
	}

	/**
	 * Opens the recording in Replay Mod's viewer and waits for the scene's actors to show up in the replayed world,
	 * the way someone would check a recording by eye. Replay Mod has no public API, so its viewer is reached by
	 * reflection; the scene's own mod is not needed for the actors to appear, since they are plain vanilla entities.
	 */
	private static void playBack(ClientGameTestContext context, Path replay) {
		context.runOnClient(client -> replayMod("startReplay", replay.toFile()));
		context.waitFor(client -> client.level != null && replayed(client) > 0, 20 * 60);
		context.waitTicks(40);
		int actors = context.computeOnClient(RecorderClientGameTest::replayed);
		System.out.println("Replay Mod played the recording back with " + actors + " scene actors in view");
		Screenshots.printThumbnail(context.takeScreenshot("scenescripter-replay-playback"), "replay-playback");
		context.runOnClient(client -> {
			Object handler = replayMod("getReplayHandler", null);
			if (handler != null) {
				try {
					handler.getClass().getMethod("endReplay").invoke(handler);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError("Could not end the replay", e);
				}
			}
		});
		context.waitFor(client -> client.level == null, 20 * 30);
	}

	/** Sets Flashback to save a finished recording straight to its replays folder instead of asking for a name. */
	private static void flashbackQuicksave() {
		try {
			Object config = flashback("getConfig");
			Object controls = config.getClass().getField("recordingControls").get(config);
			controls.getClass().getField("quicksave").setBoolean(controls, true);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Flashback's recording settings could not be reached", e);
		}
	}

	private static boolean flashbackRecording() {
		try {
			return Class.forName("com.moulberry.flashback.Flashback").getField("RECORDER").get(null) != null;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Flashback's recorder could not be reached", e);
		}
	}

	/** Opens the recording in Flashback's viewer, waits for the scene's actors to show up and leaves again. */
	private static void playBackInFlashback(ClientGameTestContext context, Path replay) {
		// The test harness puts off world loads asked for inside a client task and starts them again later, which
		// would lose the replay server Flashback swaps in; queue the opening on the client from this thread instead.
		net.minecraft.client.Minecraft game = context.computeOnClient(client -> client);
		game.execute(() -> {
			try {
				Class.forName("com.moulberry.flashback.Flashback").getMethod("openReplayWorld", Path.class).invoke(null, replay);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("Flashback could not open the recording", e);
			}
		});
		// Flashback opens a replay paused at its start: press play once it has loaded, as a viewer would.
		boolean started = false;
		for (int wait = 0; wait < 12 && !started; wait++) {
			context.waitTicks(100);
			String state = context.computeOnClient(client -> {
				Object server = flashback("getReplayServer");
				int tick = -1;
				if (server != null) {
					try {
						server.getClass().getField("replayPaused").setBoolean(server, false);
						tick = (int) server.getClass().getMethod("getReplayTick").invoke(server);
					} catch (ReflectiveOperationException e) {
						throw new AssertionError("Flashback's replay could not be played", e);
					}
				}
				return "level " + (client.level != null) + ", replay tick " + tick + ", entities " + entityCounts(client)
						+ ", screen " + (client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName());
			});
			started = context.computeOnClient(client -> client.level != null && replayed(client) > 0);
			System.out.println("Flashback replay after " + (wait + 1) * 5 + " s: " + state);
		}
		if (!started) {
			Screenshots.printThumbnail(context.takeScreenshot("scenescripter-flashback-stuck"), "flashback-stuck");
			throw new AssertionError("The scene's actors never showed up in Flashback's replay");
		}
		context.waitTicks(40);
		int actors = context.computeOnClient(RecorderClientGameTest::replayed);
		System.out.println("Flashback played the recording back with " + actors + " scene actors in view");
		Screenshots.printThumbnail(context.takeScreenshot("scenescripter-flashback-playback"), "flashback-playback");
		leaveReplay(context);
	}

	/** Leaves a replay world the way the test harness leaves singleplayer, and goes back to the title screen. */
	private static void leaveReplay(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.level != null) {
				client.level.disconnect(Component.translatable("menu.savingLevel"));
			}
			client.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
		});
		context.waitFor(client -> client.level == null, 20 * 60);
		context.waitTicks(2);
		context.setScreen(TitleScreen::new);
	}

	/** Calls one of Flashback's static methods without arguments. */
	private static Object flashback(String method) {
		try {
			return Class.forName("com.moulberry.flashback.Flashback").getMethod(method).invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Flashback could not be reached (" + method + ")", e);
		}
	}

	/** Calls a method of Replay Mod's replay module, with one file argument or none. */
	private static Object replayMod(String method, java.io.File file) {
		try {
			Class<?> mod = Class.forName("com.replaymod.replay.ReplayModReplay");
			Object instance = mod.getField("instance").get(null);
			return file == null ? mod.getMethod(method).invoke(instance)
					: mod.getMethod(method, java.io.File.class).invoke(instance, file);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Replay Mod's viewer could not be reached (" + method + ")", e);
		}
	}

	/** Mannequins and zombies in the replayed world: the tutorial's knight and the zombie he fights. */
	private static int replayed(net.minecraft.client.Minecraft client) {
		int count = 0;
		if (client.level != null) {
			for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.decoration.Mannequin
						|| e instanceof net.minecraft.world.entity.monster.zombie.Zombie) {
					count++;
				}
			}
		}
		return count;
	}

	/** How many entities of each kind the client has, for the log. */
	private static java.util.Map<String, Integer> entityCounts(net.minecraft.client.Minecraft client) {
		java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
		if (client.level != null) {
			for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
				counts.merge(e.getClass().getSimpleName(), 1, Integer::sum);
			}
		}
		return counts;
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0;
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
