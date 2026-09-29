package io.github.firestormfmd.scenescripter.core.anim;

import java.util.Objects;

/**
 * A channel value at a tick.
 *
 * @param interpolation how to move from this keyframe to the next one
 * @param handles custom Bézier handles, or null for automatic ones. Only used by single-component channels.
 * @param generatedBy ID of the event whose solved result produced this keyframe, or null for keyframes the user set.
 *                    The solver may replace generated keyframes; user keyframes are never touched.
 */
public record Keyframe<T>(int tick, T value, Interpolation interpolation, Handles handles, String generatedBy) {
	public Keyframe {
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(interpolation, "interpolation");
	}

	public static <T> Keyframe<T> of(int tick, T value) {
		return new Keyframe<>(tick, value, Interpolation.LINEAR, null, null);
	}

	public static <T> Keyframe<T> of(int tick, T value, Interpolation interpolation) {
		return new Keyframe<>(tick, value, interpolation, null, null);
	}

	public boolean isGenerated() {
		return generatedBy != null;
	}

	public Keyframe<T> withTick(int newTick) {
		return new Keyframe<>(newTick, value, interpolation, handles, generatedBy);
	}

	public Keyframe<T> withValue(T newValue) {
		return new Keyframe<>(tick, newValue, interpolation, handles, generatedBy);
	}

	/** Returns a copy the solver no longer owns, as happens when the user edits a generated keyframe. */
	public Keyframe<T> detached() {
		return new Keyframe<>(tick, value, interpolation, handles, null);
	}
}
