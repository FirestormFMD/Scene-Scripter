package io.github.firestormfmd.scenescripter.core.path;

import java.util.List;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * A planned motion clip: one sample per tick from {@link #startTick()} to {@link #endTick()}, plus the problems
 * found and the moments worth adding effects for.
 *
 * @param jumpTicks ticks the object takes off
 * @param landings ticks the object lands, with how far it fell
 */
public record Locomotion(int startTick, List<MotionSample> samples, List<PathIssue> issues, List<Integer> jumpTicks,
		List<Landing> landings) {
	public record Landing(int tick, double fallDistance) {
	}

	public Locomotion {
		if (samples.isEmpty()) {
			throw new IllegalArgumentException("A locomotion needs at least one sample");
		}
		samples = List.copyOf(samples);
		issues = List.copyOf(issues);
		jumpTicks = List.copyOf(jumpTicks);
		landings = List.copyOf(landings);
	}

	public int endTick() {
		return startTick + samples.size() - 1;
	}

	/** Sample on a whole tick, clamped to the clip. */
	public MotionSample sampleAt(int tick) {
		int i = Math.clamp(tick - startTick, 0, samples.size() - 1);
		return samples.get(i);
	}

	/** Position between ticks, for smooth previews. */
	public Vec3 positionAt(double tick) {
		double t = Math.clamp(tick - startTick, 0, samples.size() - 1);
		int i = (int) Math.floor(t);
		if (i >= samples.size() - 1) {
			return samples.getLast().pos();
		}
		return Vec3.lerp(samples.get(i).pos(), samples.get(i + 1).pos(), t - i);
	}

	/** Facing between ticks, turning the short way round. */
	public float yawAt(double tick) {
		double t = Math.clamp(tick - startTick, 0, samples.size() - 1);
		int i = (int) Math.floor(t);
		if (i >= samples.size() - 1) {
			return samples.getLast().yaw();
		}
		float a = samples.get(i).yaw();
		float b = samples.get(i + 1).yaw();
		return (float) (a + LocomotionPlanner.wrapDegrees(b - a) * (t - i));
	}

	public boolean hasIssues() {
		return !issues.isEmpty();
	}
}
