package io.github.firestormfmd.scenescripter.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.core.journal.AppliedChangeSet;
import io.github.firestormfmd.scenescripter.core.journal.BlockChange;
import io.github.firestormfmd.scenescripter.core.journal.JournalStore;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;

/**
 * Keeps the list of applied block changes on disk ({@code journal.json.gz}), written before any block changes, so
 * a crash mid-scene is repaired the next time the world loads.
 */
public final class JournalFile implements JournalStore<SavedBlock> {
	private final Path file;
	private final String dimension;

	/**
	 * @param dimension ID of the dimension the scene changes blocks in, saved so recovery restores the right one
	 */
	public JournalFile(Path file, String dimension) {
		this.file = file;
		this.dimension = dimension;
	}

	/** A journal left behind by an earlier session. */
	public record Leftover(String dimension, List<AppliedChangeSet<SavedBlock>> sets) {
	}

	@Override
	public void write(List<AppliedChangeSet<SavedBlock>> applied) {
		try {
			if (applied.isEmpty()) {
				Files.deleteIfExists(file);
				return;
			}
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8)) {
				JsonObject root = new JsonObject();
				root.addProperty("dimension", dimension);
				root.add("sets", toJson(applied));
				w.write(root.toString());
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			SceneScripter.LOGGER.error("Could not write the scene block journal to {}", file, e);
		}
	}

	/** Reads a journal left behind by a previous session, if there is one. */
	public static java.util.Optional<Leftover> read(Path file) {
		if (!Files.exists(file)) {
			return java.util.Optional.empty();
		}
		try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			return java.util.Optional.of(new Leftover(root.get("dimension").getAsString(),
					fromJson(root.getAsJsonArray("sets"))));
		} catch (IOException | RuntimeException e) {
			SceneScripter.LOGGER.error("Could not read the scene block journal {}", file, e);
			return java.util.Optional.empty();
		}
	}

	public void delete() {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			SceneScripter.LOGGER.warn("Could not delete {}", file, e);
		}
	}

	private static JsonArray toJson(List<AppliedChangeSet<SavedBlock>> applied) {
		JsonArray sets = new JsonArray();
		for (AppliedChangeSet<SavedBlock> set : applied) {
			JsonObject o = new JsonObject();
			o.addProperty("id", set.id());
			o.addProperty("tick", set.tick());
			JsonArray blocks = new JsonArray();
			for (BlockChange<SavedBlock> change : set.previous()) {
				JsonObject b = new JsonObject();
				b.addProperty("x", change.pos().x());
				b.addProperty("y", change.pos().y());
				b.addProperty("z", change.pos().z());
				b.add("state", BlockState.CODEC.encodeStart(JsonOps.INSTANCE, change.state().state()).result().orElseThrow());
				if (change.state().blockEntity() != null) {
					b.add("be", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, change.state().blockEntity()));
				}
				blocks.add(b);
			}
			o.add("blocks", blocks);
			sets.add(o);
		}
		return sets;
	}

	private static List<AppliedChangeSet<SavedBlock>> fromJson(JsonArray sets) {
		List<AppliedChangeSet<SavedBlock>> out = new ArrayList<>();
		for (JsonElement e : sets) {
			JsonObject o = e.getAsJsonObject();
			List<BlockChange<SavedBlock>> blocks = new ArrayList<>();
			for (JsonElement be : o.getAsJsonArray("blocks")) {
				JsonObject b = be.getAsJsonObject();
				BlockState state = BlockState.CODEC.parse(JsonOps.INSTANCE, b.get("state")).result().orElseThrow();
				CompoundTag tag = null;
				if (b.has("be")) {
					Tag t = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, b.get("be"));
					tag = t instanceof CompoundTag c ? c : null;
				}
				blocks.add(new BlockChange<>(new BlockPos(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt()),
						new SavedBlock(state, tag)));
			}
			out.add(new AppliedChangeSet<>(o.get("id").getAsString(), o.get("tick").getAsInt(), blocks));
		}
		return out;
	}
}
