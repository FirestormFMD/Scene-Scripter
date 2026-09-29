package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Objects;

/**
 * Something that happens at a point along a path.
 *
 * @param at position along the path as a fraction of its length (0 to 1), so markers stay put when the path is
 *           reshaped
 * @param ticks how long to wait, for {@link Kind#WAIT}
 * @param gait the gait to switch to, for {@link Kind#GAIT}
 */
public record PathMarker(double at, Kind kind, int ticks, Gait gait) {
	public enum Kind {
		/** Jump here even if the ground is flat. */
		JUMP("jump"),
		/** Stand still for a number of ticks. */
		WAIT("wait"),
		/** Switch gait from here on. */
		GAIT("gait");

		private final String id;

		Kind(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Kind byId(String id) {
			for (Kind k : values()) {
				if (k.id.equals(id)) {
					return k;
				}
			}
			throw new IllegalArgumentException("Unknown path marker: " + id);
		}
	}

	public PathMarker {
		Objects.requireNonNull(kind, "kind");
		if (at < 0 || at > 1) {
			throw new IllegalArgumentException("Marker position must be between 0 and 1: " + at);
		}
		if (kind == Kind.WAIT && ticks <= 0) {
			throw new IllegalArgumentException("Wait markers need a positive tick count");
		}
		if (kind == Kind.GAIT) {
			Objects.requireNonNull(gait, "gait");
		}
	}

	public static PathMarker jump(double at) {
		return new PathMarker(at, Kind.JUMP, 0, null);
	}

	public static PathMarker waitFor(double at, int ticks) {
		return new PathMarker(at, Kind.WAIT, ticks, null);
	}

	public static PathMarker gait(double at, Gait gait) {
		return new PathMarker(at, Kind.GAIT, 0, gait);
	}
}
