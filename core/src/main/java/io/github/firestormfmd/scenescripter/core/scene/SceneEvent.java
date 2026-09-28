package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Map;
import java.util.Objects;

/**
 * A one-off action on an object's timeline, such as an attack, a hurt, a death or an explosion.
 *
 * @param type event type ID, such as {@code attack} or {@code explode}
 * @param target ID of the object this event acts on, or null
 * @param params extra settings; values are strings, numbers or booleans
 * @param generatedBy ID of the event whose solved result produced this one, or null if the user added it
 */
public record SceneEvent(String id, int tick, String type, String target, Map<String, Object> params,
		String generatedBy) {
	public SceneEvent {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(type, "type");
		params = Map.copyOf(params);
		for (Map.Entry<String, Object> e : params.entrySet()) {
			Object v = e.getValue();
			if (!(v instanceof String || v instanceof Number || v instanceof Boolean)) {
				throw new IllegalArgumentException("Event parameter " + e.getKey() + " must be a string, number or boolean");
			}
		}
	}

	public static SceneEvent of(String id, int tick, String type, String target) {
		return new SceneEvent(id, tick, type, target, Map.of(), null);
	}

	public boolean isGenerated() {
		return generatedBy != null;
	}

	public SceneEvent withTick(int newTick) {
		return new SceneEvent(id, newTick, type, target, params, generatedBy);
	}
}
