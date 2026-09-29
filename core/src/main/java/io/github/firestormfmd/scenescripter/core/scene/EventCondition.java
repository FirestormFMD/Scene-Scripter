package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.anim.Channel;

/**
 * An event's {@code if} parameter: a test on a channel at the event's tick, such as {@code lives <= 0} or
 * {@code angry == true}. Names refer to the event's own object; {@code scene.} reads the scene track. An event
 * without a condition always happens; a condition that can't be read also lets it happen, so a typo never
 * silently removes an event.
 */
public final class EventCondition {
	private static final String[] OPS = {"<=", ">=", "==", "!=", "<", ">"};

	private EventCondition() {
	}

	/** Whether the event happens at its tick. */
	public static boolean holds(SceneEvent event, SceneObject owner, Scene scene) {
		return holds(event.params().get("if") instanceof String s ? s : null, owner, scene, event.tick());
	}

	public static boolean holds(String condition, SceneObject owner, Scene scene, int tick) {
		if (condition == null || condition.isBlank()) {
			return true;
		}
		String c = condition.trim();
		for (String op : OPS) {
			int i = c.indexOf(op);
			if (i > 0) {
				String name = c.substring(0, i).trim();
				String wanted = c.substring(i + op.length()).trim();
				Optional<Object> value = read(name, owner, scene, tick);
				if (value.isEmpty()) {
					return true;
				}
				return compare(value.get(), op, wanted);
			}
		}
		return true;
	}

	private static Optional<Object> read(String name, SceneObject owner, Scene scene, int tick) {
		SceneObject from = owner;
		String channel = name;
		if (name.startsWith("scene.")) {
			from = scene.tracks();
			channel = name.substring("scene.".length());
		}
		Optional<Channel<?>> ch = from.channel(channel);
		if (ch.isPresent()) {
			return Optional.of(ch.get().valueAt(tick));
		}
		return BuiltInChannels.byName(channel).map(spec -> spec.defaultValue());
	}

	private static boolean compare(Object value, String op, String wanted) {
		if (value instanceof Boolean b) {
			boolean w = Boolean.parseBoolean(wanted);
			return switch (op) {
				case "==" -> b == w;
				case "!=" -> b != w;
				default -> true;
			};
		}
		if (value instanceof Number n) {
			double w;
			try {
				w = Double.parseDouble(wanted);
			} catch (NumberFormatException e) {
				return true;
			}
			double v = n.doubleValue();
			return switch (op) {
				case "<=" -> v <= w;
				case ">=" -> v >= w;
				case "==" -> Math.abs(v - w) < 1.0e-9;
				case "!=" -> Math.abs(v - w) >= 1.0e-9;
				case "<" -> v < w;
				case ">" -> v > w;
				default -> true;
			};
		}
		String s = String.valueOf(value);
		return switch (op) {
			case "==" -> s.equals(wanted);
			case "!=" -> !s.equals(wanted);
			default -> true;
		};
	}
}
