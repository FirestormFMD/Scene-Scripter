package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Objects;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * A control point of a motion path.
 *
 * @param handleIn Bézier handle towards the previous point as an offset from {@code pos}, or null for automatic
 * @param handleOut Bézier handle towards the next point as an offset from {@code pos}, or null for automatic
 */
public record PathPoint(Vec3 pos, Vec3 handleIn, Vec3 handleOut) {
	public PathPoint {
		Objects.requireNonNull(pos, "pos");
	}

	public static PathPoint at(Vec3 pos) {
		return new PathPoint(pos, null, null);
	}

	public static PathPoint at(double x, double y, double z) {
		return at(new Vec3(x, y, z));
	}

	public PathPoint withPos(Vec3 newPos) {
		return new PathPoint(newPos, handleIn, handleOut);
	}
}
