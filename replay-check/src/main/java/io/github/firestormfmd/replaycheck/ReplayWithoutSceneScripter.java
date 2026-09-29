package io.github.firestormfmd.replaycheck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.zombie.Zombie;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Plays scene recordings back in Replay Mod in a game without Scene Scripter, and checks the scene's actors show
 * up: they are plain vanilla entities, so a recording must not need the mod to be watched. The recordings come
 * from the folder in the {@code SCENE_RECORDINGS} environment variable; without it there is nothing to check.
 */
@SuppressWarnings("UnstableApiUsage")
public class ReplayWithoutSceneScripter implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (FabricLoader.getInstance().isModLoaded("scenescripter")) {
			throw new AssertionError("This check must run without Scene Scripter");
		}
		String folder = System.getenv("SCENE_RECORDINGS");
		if (folder == null || folder.isBlank()) {
			System.out.println("SCENE_RECORDINGS is not set; no recordings to play back");
			return;
		}
		List<Path> recordings = recordings(Path.of(folder));
		if (recordings.isEmpty()) {
			throw new AssertionError("No .mcpr recordings under " + folder);
		}
		// The largest recording is the one of the played tutorial scene.
		Path replay = recordings.stream().max(java.util.Comparator.comparingLong(ReplayWithoutSceneScripter::size)).orElseThrow();
		System.out.println("Playing " + replay.getFileName() + " (" + size(replay) + " bytes) without Scene Scripter");
		context.runOnClient(client -> replayMod("startReplay", replay.toFile()));
		// Replay Mod may stop on a screen first (such as a note about mods the recording was made with); log what
		// is open while waiting, and show it if the replay never starts.
		boolean started = false;
		for (int wait = 0; wait < 12 && !started; wait++) {
			context.waitTicks(100);
			started = context.computeOnClient(client -> client.level != null && actors(client) > 0);
			String screen = context.computeOnClient(client -> client.gui.screen() == null ? "none"
					: client.gui.screen().getClass().getName() + " \"" + client.gui.screen().getTitle().getString() + "\"");
			System.out.println("Replay after " + (wait + 1) * 5 + " s: level " + context.computeOnClient(client -> client.level != null)
					+ ", screen " + screen);
		}
		if (!started) {
			printThumbnail(context.takeScreenshot("replay-without-scene-scripter-stuck"));
			throw new AssertionError("The recording did not start playing without Scene Scripter");
		}
		context.waitTicks(40);
		int actors = context.computeOnClient(ReplayWithoutSceneScripter::actors);
		System.out.println("The recording played back without Scene Scripter with " + actors + " scene actors in view");
		context.takeScreenshot("replay-without-scene-scripter");
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

	/** A small JPEG of a screenshot, printed to the log as base64 lines, to look at from CI. */
	private static void printThumbnail(Path png) {
		try {
			java.awt.image.BufferedImage full = javax.imageio.ImageIO.read(png.toFile());
			java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(256, 144, java.awt.image.BufferedImage.TYPE_INT_RGB);
			var g = small.createGraphics();
			g.drawImage(full, 0, 0, 256, 144, null);
			g.dispose();
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			javax.imageio.ImageIO.write(small, "jpg", bytes);
			String text = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
			for (int i = 0; i < text.length(); i += 2000) {
				System.out.println("THUMB stuck " + i / 2000 + " " + text.substring(i, Math.min(text.length(), i + 2000)));
			}
		} catch (IOException e) {
			System.out.println("Could not print the screenshot: " + e);
		}
	}

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
	private static int actors(Minecraft client) {
		int count = 0;
		if (client.level != null) {
			for (Entity e : client.level.entitiesForRendering()) {
				if (e instanceof Mannequin || e instanceof Zombie) {
					count++;
				}
			}
		}
		return count;
	}

	private static List<Path> recordings(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.walk(dir)) {
			return s.filter(p -> p.getFileName().toString().endsWith(".mcpr")).toList();
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
