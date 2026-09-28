package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Objects;

/**
 * A motion path placed on one object's timeline.
 *
 * @param endTick the pinned end tick for {@link TimingMode#FIT}; ignored for {@link TimingMode#SPEED}
 * @param lateralOffset sideways offset from the path in blocks, positive to the right of the direction of travel.
 *                      Lets several objects share one path in formation.
 */
public record MotionClip(String pathId, int startTick, TimingMode timing, int endTick, double lateralOffset) {
	public MotionClip {
		Objects.requireNonNull(pathId, "pathId");
		Objects.requireNonNull(timing, "timing");
		if (timing == TimingMode.FIT && endTick <= startTick) {
			throw new IllegalArgumentException("A fitted clip must end after it starts");
		}
	}

	public static MotionClip atSpeed(String pathId, int startTick) {
		return new MotionClip(pathId, startTick, TimingMode.SPEED, -1, 0);
	}

	public static MotionClip fitted(String pathId, int startTick, int endTick) {
		return new MotionClip(pathId, startTick, TimingMode.FIT, endTick, 0);
	}

	public MotionClip withLateralOffset(double offset) {
		return new MotionClip(pathId, startTick, timing, endTick, offset);
	}
}
