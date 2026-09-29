package io.github.firestormfmd.scenescripter.core.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.firestormfmd.scenescripter.core.io.SceneCodec;
import io.github.firestormfmd.scenescripter.core.io.SceneFormatException;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;

/**
 * Takes as JSON. Samples are stored as compact arrays because a take has one per tick:
 * {@code [tick, x, y, z, bodyYaw, headYaw, headPitch, flags, pose]} with flags bit 0 on ground, 1 sneaking,
 * 2 sprinting, 3 swimming. Equipment is stored only where it changes.
 */
public final class TakeCodec {
	private TakeCodec() {
	}

	public static JsonObject toJson(Take take) {
		JsonObject o = new JsonObject();
		o.addProperty("id", take.id());
		o.addProperty("object", take.objectId());
		o.addProperty("name", take.name());
		o.addProperty("recordedAt", take.recordedAt());
		JsonArray samples = new JsonArray();
		JsonArray equipment = new JsonArray();
		Map<String, String> lastEquipment = null;
		for (CaptureSample s : take.samples()) {
			JsonArray a = new JsonArray();
			a.add(s.tick());
			a.add(round(s.pos().x()));
			a.add(round(s.pos().y()));
			a.add(round(s.pos().z()));
			a.add(round(s.bodyYaw()));
			a.add(round(s.headYaw()));
			a.add(round(s.headPitch()));
			a.add((s.onGround() ? 1 : 0) | (s.sneaking() ? 2 : 0) | (s.sprinting() ? 4 : 0) | (s.swimming() ? 8 : 0));
			a.add(s.pose());
			samples.add(a);
			if (!s.equipment().equals(lastEquipment)) {
				JsonObject e = new JsonObject();
				e.addProperty("tick", s.tick());
				s.equipment().forEach(e::addProperty);
				equipment.add(e);
				lastEquipment = s.equipment();
			}
		}
		o.add("samples", samples);
		o.add("equipment", equipment);
		JsonArray events = new JsonArray();
		take.events().forEach(e -> events.add(SceneCodec.event(e)));
		o.add("events", events);
		return o;
	}

	public static Take fromJson(JsonObject o) throws SceneFormatException {
		try {
			List<Map<String, String>> changes = new ArrayList<>();
			List<Integer> changeTicks = new ArrayList<>();
			for (JsonElement el : o.getAsJsonArray("equipment")) {
				JsonObject e = el.getAsJsonObject();
				changeTicks.add(e.get("tick").getAsInt());
				Map<String, String> slots = new java.util.LinkedHashMap<>();
				for (var entry : e.entrySet()) {
					if (!entry.getKey().equals("tick")) {
						slots.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
				changes.add(slots);
			}
			List<CaptureSample> samples = new ArrayList<>();
			int change = -1;
			for (JsonElement el : o.getAsJsonArray("samples")) {
				JsonArray a = el.getAsJsonArray();
				int tick = a.get(0).getAsInt();
				while (change + 1 < changeTicks.size() && changeTicks.get(change + 1) <= tick) {
					change++;
				}
				int flags = a.get(7).getAsInt();
				samples.add(new CaptureSample(tick, new Vec3(a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble()),
						a.get(4).getAsFloat(), a.get(5).getAsFloat(), a.get(6).getAsFloat(), (flags & 1) != 0,
						(flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, a.get(8).getAsString(),
						change >= 0 ? changes.get(change) : Map.of()));
			}
			List<SceneEvent> events = new ArrayList<>();
			for (JsonElement el : o.getAsJsonArray("events")) {
				events.add(SceneCodec.readEvent(el.getAsJsonObject()));
			}
			return new Take(o.get("id").getAsString(), o.get("object").getAsString(), o.get("name").getAsString(),
					o.get("recordedAt").getAsLong(), samples, events);
		} catch (RuntimeException e) {
			throw new SceneFormatException("Bad take: " + e.getMessage(), e);
		}
	}

	private static double round(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}
}
