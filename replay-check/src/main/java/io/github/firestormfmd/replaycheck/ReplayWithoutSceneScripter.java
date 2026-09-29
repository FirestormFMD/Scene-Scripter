package io.github.firestormfmd.replaycheck;

import java.io.IOException;
import java.lang.reflect.Method;
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
		boolean clickedThrough = false;
		for (int wait = 0; wait < 12 && !started; wait++) {
			context.waitTicks(100);
			started = context.computeOnClient(client -> client.level != null && actors(client) > 0);
			boolean warned = context.computeOnClient(client -> client.gui.screen() != null
					&& client.gui.screen().getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains("incompatib"));
			if (warned && !clickedThrough) {
				// Replay Mod warns that the recording was made with mods that aren't installed (Scene Scripter's network
				// channels); someone without the mod reads it and loads the replay anyway, and so does this check.
				clickedThrough = true;
				String result = context.computeOnClient(client -> loadAnyway(client.gui.screen()));
				System.out.println("Replay Mod's warning: " + result);
			}
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

	/**
	 * Presses the button on Replay Mod's incompatibility warning that loads the replay anyway. Replay Mod's screens
	 * are built from its own GUI library, so the buttons are found by walking the screen's objects.
	 */
	private static String loadAnyway(Object screen) {
		List<Object> clickables = new java.util.ArrayList<>();
		List<String> texts = new java.util.ArrayList<>();
		walk(screen, 0, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), clickables, texts);
		StringBuilder out = new StringBuilder("text " + texts + ", buttons [");
		Object chosen = null;
		for (Object c : clickables) {
			String label = text(c);
			out.append(label).append("; ");
			String l = label.toLowerCase(java.util.Locale.ROOT);
			if (chosen == null && !l.isBlank() && l.matches(".*(load|continue|anyway|ignore|proceed|yes|ok).*")
					&& !l.matches(".*(cancel|back|no\\b|abort).*")) {
				chosen = c;
			}
		}
		out.append("]");
		if (chosen == null) {
			return out + ", none to press";
		}
		Method click = method(chosen.getClass(), "onClick");
		try {
			click.invoke(chosen);
		} catch (ReflectiveOperationException e) {
			return out + ", pressing \"" + text(chosen) + "\" failed: " + e;
		}
		return out + ", pressed \"" + text(chosen) + "\"";
	}

	private static void walk(Object o, int depth, java.util.Set<Object> seen, List<Object> clickables, List<String> texts) {
		if (o == null || depth > 10 || seen.size() > 20_000 || !seen.add(o)) {
			return;
		}
		// Only plain collections are followed: other iterables (such as service loaders) do work when iterated.
		try {
			if (o instanceof java.util.Map<?, ?> map) {
				for (Object k : new java.util.ArrayList<>(map.keySet())) {
					walk(k, depth + 1, seen, clickables, texts);
				}
				for (Object v : new java.util.ArrayList<>(map.values())) {
					walk(v, depth + 1, seen, clickables, texts);
				}
				return;
			}
			if (o instanceof java.util.Collection<?> items) {
				for (Object i : new java.util.ArrayList<>(items)) {
					walk(i, depth + 1, seen, clickables, texts);
				}
				return;
			}
		} catch (RuntimeException | Error e) {
			return;
		}
		if (o instanceof Iterable<?>) {
			return;
		}
		if (o instanceof Object[] array) {
			for (Object i : array) {
				walk(i, depth + 1, seen, clickables, texts);
			}
			return;
		}
		Class<?> type = o.getClass();
		if (!type.getName().startsWith("com.replaymod")) {
			return;
		}
		if (method(type, "onClick") != null) {
			clickables.add(o);
		} else if (method(type, "getText") != null) {
			String t = text(o);
			if (!t.isBlank()) {
				texts.add(t);
			}
		}
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (java.lang.reflect.Field f : c.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
					continue;
				}
				try {
					f.setAccessible(true);
					walk(f.get(o), depth + 1, seen, clickables, texts);
				} catch (RuntimeException | Error | IllegalAccessException ignored) {
					// a field the module system keeps closed; its contents aren't Replay Mod's
				}
			}
		}
	}

	/** The label or text of a Replay Mod GUI element, or an empty string. */
	private static String text(Object element) {
		for (String name : new String[] {"getLabel", "getText"}) {
			Method m = method(element.getClass(), name);
			if (m != null) {
				try {
					Object value = m.invoke(element);
					if (value instanceof net.minecraft.network.chat.Component component) {
						return component.getString();
					}
					if (value instanceof List<?> lines) {
						return lines.toString();
					}
					return String.valueOf(value);
				} catch (ReflectiveOperationException | RuntimeException e) {
					return "";
				}
			}
		}
		return "";
	}

	/** A method without parameters, declared by the class or a superclass, made accessible; or null. */
	private static Method method(Class<?> type, String name) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException | RuntimeException ignored) {
				// look further up
			}
		}
		return null;
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
