package io.github.firestormfmd.scenescripter.actor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.firestormfmd.scenescripter.SceneScripter;

/**
 * Mob-specific events from {@code data/scenescripter/mob_events.json}: things like an iron golem offering a
 * flower or a sheep eating grass, each played as the vanilla entity event a client animates from. Covering more
 * mobs only needs more entries in the file.
 */
public final class MobEvents {
	/** One mob event: its ID, what the editor calls it, the entity types that have it and the entity event byte. */
	public record MobEvent(String id, String label, List<String> types, byte entityEvent) {
	}

	private static final List<MobEvent> ALL = load();

	private MobEvents() {
	}

	private static List<MobEvent> load() {
		List<MobEvent> out = new ArrayList<>();
		try (InputStream in = MobEvents.class.getResourceAsStream("/data/scenescripter/mob_events.json")) {
			if (in == null) {
				return out;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement el : root.getAsJsonArray("events")) {
				JsonObject o = el.getAsJsonObject();
				List<String> types = new ArrayList<>();
				o.getAsJsonArray("types").forEach(t -> types.add(t.getAsString()));
				out.add(new MobEvent(o.get("id").getAsString(), o.get("label").getAsString(), List.copyOf(types),
						(byte) o.get("entity_event").getAsInt()));
			}
		} catch (Exception e) {
			SceneScripter.LOGGER.error("Could not read the mob events file", e);
		}
		return List.copyOf(out);
	}

	/** The events an entity type has, except the attack override. */
	public static List<MobEvent> forType(String entityType) {
		return ALL.stream().filter(e -> !e.id().equals("attack") && e.types().contains(entityType)).toList();
	}

	public static Optional<MobEvent> find(String entityType, String id) {
		// "*" in an entry's types lets every mob play it without offering it as a button for each.
		return ALL.stream().filter(e -> e.id().equals(id) && (e.types().contains(entityType) || e.types().contains("*")))
				.findFirst();
	}
}
