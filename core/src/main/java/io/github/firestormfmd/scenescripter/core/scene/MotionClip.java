package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Objects;

/**
 * A motion path placed on one object's timeline.
 *
 * @param endTick the pinned end tick for {@link TimingMode#FIT}; ignored for {@link TimingMode#SPEED}
 * @param lateralOffset sideways offset from the path in blocks, positive to the right of the direction of travel.
 *                      Lets several objects share one path in formation.
 * @param speedScale multiplies the path's speeds for this object alone, so a crowd on one path doesn't move in
 *                   lockstep; ignored for {@link TimingMode#FIT}
 */
public record MotionClip(String pathId, int startTick, TimingMode timing, int endTick, double lateralOffset,
		double speedScale) {
	public MotionClip(String pathId, int startTick, TimingMode timing, int endTick, double lateralOffset) {
		this(pathId, startTick, timing, endTick, lateralOffset, 1.0);
	}

	public MotionClip {
		if (!(speedScale > 0)) {
			throw new IllegalArgumentException("Speed scale must be positive: " + speedScale);
		}
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
		return new MotionClip(pathId, startTick, timing, endTick, offset, speedScale);
	}

	public MotionClip withSpeedScale(double scale) {
		return new MotionClip(pathId, startTick, timing, endTick, lateralOffset, scale);
	}

	/** The same clip starting {@code delta} ticks later (earlier if negative). */
	public MotionClip shifted(int delta) {
		return new MotionClip(pathId, startTick + delta, timing, timing == TimingMode.FIT ? endTick + delta : endTick,
				lateralOffset, speedScale);
	}
}
