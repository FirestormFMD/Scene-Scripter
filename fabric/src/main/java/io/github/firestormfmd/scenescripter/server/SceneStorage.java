package io.github.firestormfmd.scenescripter.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.core.io.SceneCodec;
import io.github.firestormfmd.scenescripter.core.io.SceneFormatException;
import io.github.firestormfmd.scenescripter.core.scene.Scene;

/**
 * Scene files in {@code <world>/scene_scripter/scenes/<name>.json}, with the previous few saves kept as backups.
 */
public final class SceneStorage {
	private static final int BACKUPS = 5;
	/** Scenes bigger than this many characters of JSON are saved gzipped. */
	private static final int GZIP_ABOVE = 1 << 20;

	private final Path root;

	public SceneStorage(MinecraftServer server) {
		this.root = server.getWorldPath(LevelResource.ROOT).resolve("scene_scripter");
	}

	public Path root() {
		return root;
	}

	public Path journalFile() {
		return root.resolve("journal.json.gz");
	}

	/** Lowercase letters, digits, underscores and dashes only, so names are safe as file names. */
	public static Optional<String> cleanName(String name) {
		String clean = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (clean.isEmpty() || clean.length() > 64 || !clean.matches("[a-z0-9_\\-]+")) {
			return Optional.empty();
		}
		return Optional.of(clean);
	}

	/** The scene's file: {@code <name>.json}, or {@code <name>.json.gz} for a scene too big to keep as plain text. */
	private Path file(String name) {
		Path gz = root.resolve("scenes").resolve(name + ".json.gz");
		return Files.exists(gz) ? gz : root.resolve("scenes").resolve(name + ".json");
	}

	private static boolean gzipped(Path f) {
		return f.getFileName().toString().endsWith(".gz");
	}

	public List<String> list() {
		List<String> names = new ArrayList<>();
		Path dir = root.resolve("scenes");
		if (!Files.isDirectory(dir)) {
			return names;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.{json,json.gz}")) {
			for (Path f : files) {
				String n = f.getFileName().toString();
				String name = n.substring(0, n.length() - (gzipped(f) ? ".json.gz" : ".json").length());
				if (!names.contains(name)) {
					names.add(name);
				}
			}
		} catch (IOException e) {
			return names;
		}
		names.sort(String::compareTo);
		return names;
	}

	public boolean exists(String name) {
		return Files.exists(file(name));
	}

	public Scene load(String name) throws IOException, SceneFormatException {
		Path f = file(name);
		if (!gzipped(f)) {
			return SceneCodec.read(Files.readString(f, StandardCharsets.UTF_8));
		}
		try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(f))) {
			return SceneCodec.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	public void save(String name, Scene scene) throws IOException {
		Path f = file(name);
		Files.createDirectories(f.getParent());
		if (Files.exists(f)) {
			rotateBackups(name);
		}
		String json = SceneCodec.write(scene);
		// Big scenes (large crowds, raw capture keys) are gzipped; the other form is removed so only one is read.
		boolean big = json.length() > GZIP_ABOVE;
		Path target = f.resolveSibling(name + (big ? ".json.gz" : ".json"));
		Path tmp = f.resolveSibling(name + ".json.tmp");
		if (big) {
			try (var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(tmp))) {
				out.write(json.getBytes(StandardCharsets.UTF_8));
			}
		} else {
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
		}
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		Files.deleteIfExists(f.resolveSibling(name + (big ? ".json" : ".json.gz")));
	}

	public void delete(String name) throws IOException {
		Files.deleteIfExists(root.resolve("scenes").resolve(name + ".json"));
		Files.deleteIfExists(root.resolve("scenes").resolve(name + ".json.gz"));
		Files.deleteIfExists(takesFile(name));
	}

	/** Performance-capture takes are kept next to their scene, gzipped, since they hold a sample per tick. */
	private Path takesFile(String name) {
		return root.resolve("scenes").resolve(name + ".takes.gz");
	}

	public List<io.github.firestormfmd.scenescripter.core.capture.Take> loadTakes(String name) {
		Path f = takesFile(name);
		List<io.github.firestormfmd.scenescripter.core.capture.Take> takes = new ArrayList<>();
		if (!Files.exists(f)) {
			return takes;
		}
		try (var in = new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(Files.newInputStream(f)),
				StandardCharsets.UTF_8)) {
			for (var el : com.google.gson.JsonParser.parseReader(in).getAsJsonArray()) {
				takes.add(io.github.firestormfmd.scenescripter.core.capture.TakeCodec.fromJson(el.getAsJsonObject()));
			}
		} catch (IOException | SceneFormatException | RuntimeException e) {
			SceneScripter.LOGGER.error("Could not read the takes of scene {}", name, e);
		}
		return takes;
	}

	public void saveTakes(String name, List<io.github.firestormfmd.scenescripter.core.capture.Take> takes) throws IOException {
		Path f = takesFile(name);
		if (takes.isEmpty()) {
			Files.deleteIfExists(f);
			return;
		}
		Files.createDirectories(f.getParent());
		com.google.gson.JsonArray all = new com.google.gson.JsonArray();
		takes.forEach(t -> all.add(io.github.firestormfmd.scenescripter.core.capture.TakeCodec.toJson(t)));
		Path tmp = f.resolveSibling(name + ".takes.gz.tmp");
		try (var out = new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(Files.newOutputStream(tmp)),
				StandardCharsets.UTF_8)) {
			out.write(all.toString());
		}
		Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** Writes a scene to a standalone file for sharing. */
	public Path export(String name, Scene scene) throws IOException {
		Path out = root.resolve("exports").resolve(name + ".scene.json");
		Files.createDirectories(out.getParent());
		Files.writeString(out, SceneCodec.write(scene), StandardCharsets.UTF_8);
		return out;
	}

	/** Reads a shared scene file from the exports folder. */
	public Scene importFile(String fileName) throws IOException, SceneFormatException {
		Path in = root.resolve("exports").resolve(fileName);
		if (!in.normalize().startsWith(root.resolve("exports"))) {
			throw new IOException("Import files must be in the exports folder");
		}
		return SceneCodec.read(Files.readString(in, StandardCharsets.UTF_8));
	}

	private void rotateBackups(String name) throws IOException {
		Path dir = root.resolve("backups");
		Files.createDirectories(dir);
		for (int i = BACKUPS - 1; i >= 1; i--) {
			for (String ext : new String[] {".json", ".json.gz"}) {
				Path from = dir.resolve(name + "." + i + ext);
				if (Files.exists(from)) {
					Files.deleteIfExists(dir.resolve(name + "." + (i + 1) + (ext.equals(".json") ? ".json.gz" : ".json")));
					Files.move(from, dir.resolve(name + "." + (i + 1) + ext), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		Path current = file(name);
		String ext = gzipped(current) ? ".json.gz" : ".json";
		Files.deleteIfExists(dir.resolve(name + ".1" + (gzipped(current) ? ".json" : ".json.gz")));
		Files.copy(current, dir.resolve(name + ".1" + ext), StandardCopyOption.REPLACE_EXISTING);
	}
}
