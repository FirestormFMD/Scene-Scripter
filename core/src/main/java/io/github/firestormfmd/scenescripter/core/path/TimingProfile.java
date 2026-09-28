package io.github.firestormfmd.scenescripter.core.path;

import java.util.ArrayList;
import java.util.List;

import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.SpeedKey;
import io.github.firestormfmd.scenescripter.core.scene.TimingMode;

/**
 * Converts between time since the clip started and distance along the path, taking speed keys, wait markers and
 * the clip's timing mode into account.
 */
public final class TimingProfile {
	/** Distance between samples of the time table, in blocks. */
	private static final double STEP = 0.125;

	private final double length;
	/** Moving ticks (before scaling) needed to reach each sample distance. */
	private final double[] ticksAt;
	private final double scale;
	private final List<Wait> waits;
	private final double duration;
	private final boolean fitFailed;

	private record Wait(double distance, int ticks) {
	}

	public TimingProfile(MotionPath path, MotionClip clip, double length) {
		this.length = length;
		int n = Math.max(1, (int) Math.ceil(length / STEP));
		this.ticksAt = new double[n + 1];

		List<double[]> speeds = new ArrayList<>();
		speeds.add(new double[] {0, path.baseSpeed()});
		for (SpeedKey k : path.speedKeys()) {
			if (k.at() == 0) {
				speeds.getFirst()[1] = k.speed();
			} else {
				speeds.add(new double[] {k.at() * length, k.speed()});
			}
		}
		for (int i = 1; i <= n; i++) {
			double s0 = distanceOfSample(i - 1);
			double s1 = distanceOfSample(i);
			// Trapezoid rule on 1/speed; ticks = 20 * seconds.
			double inv = 0.5 * (1 / speedAt(speeds, s0) + 1 / speedAt(speeds, s1));
			ticksAt[i] = ticksAt[i - 1] + 20 * inv * (s1 - s0);
		}

		List<Wait> w = new ArrayList<>();
		int totalWait = 0;
		for (PathMarker m : path.markers()) {
			if (m.kind() == PathMarker.Kind.WAIT) {
				w.add(new Wait(m.at() * length, m.ticks()));
				totalWait += m.ticks();
			}
		}
		this.waits = List.copyOf(w);

		double moving = ticksAt[n];
		if (clip.timing() == TimingMode.FIT) {
			double available = clip.endTick() - clip.startTick() - totalWait;
			if (available <= 0 || moving <= 0) {
				// Not enough time for the waits alone; move instantly and report it.
				this.scale = moving <= 0 ? 1 : 1.0e-6;
				this.fitFailed = available <= 0 && length > 0;
			} else {
				this.scale = available / moving;
				this.fitFailed = false;
			}
		} else {
			this.scale = 1;
			this.fitFailed = false;
		}
		this.duration = moving * scale + totalWait;
	}

	private double distanceOfSample(int i) {
		return Math.min(i * STEP, length);
	}

	private static double speedAt(List<double[]> keys, double s) {
		double[] prev = keys.getFirst();
		for (int i = 1; i < keys.size(); i++) {
			double[] next = keys.get(i);
			if (s <= next[0]) {
				double span = next[0] - prev[0];
				double f = span <= 0 ? 1 : (s - prev[0]) / span;
				return prev[1] + (next[1] - prev[1]) * f;
			}
			prev = next;
		}
		return prev[1];
	}

	/** Total ticks from start to arriving at the end, including waits. May be fractional. */
	public double duration() {
		return duration;
	}

	/** True when a fitted clip is too short to hold its wait markers. */
	public boolean fitFailed() {
		return fitFailed;
	}

	/** Distance along the path at the given number of ticks after the clip starts. */
	public double distanceAt(double ticks) {
		if (ticks <= 0) {
			return 0;
		}
		double waited = 0;
		for (Wait w : waits) {
			double arrive = movingTicksTo(w.distance) + waited;
			if (ticks < arrive) {
				break;
			}
			if (ticks < arrive + w.ticks) {
				return w.distance;
			}
			waited += w.ticks;
		}
		return distanceForMovingTicks(ticks - waited);
	}

	/** Whether the object is standing at a wait marker at the given time. */
	public boolean isWaiting(double ticks) {
		double waited = 0;
		for (Wait w : waits) {
			double arrive = movingTicksTo(w.distance) + waited;
			if (ticks < arrive) {
				return false;
			}
			if (ticks < arrive + w.ticks) {
				return true;
			}
			waited += w.ticks;
		}
		return false;
	}

	private double movingTicksTo(double distance) {
		double f = distance / STEP;
		int i = Math.min((int) f, ticksAt.length - 1);
		if (i >= ticksAt.length - 1) {
			return ticksAt[ticksAt.length - 1] * scale;
		}
		double s0 = distanceOfSample(i);
		double s1 = distanceOfSample(i + 1);
		double frac = s1 > s0 ? (distance - s0) / (s1 - s0) : 0;
		return (ticksAt[i] + (ticksAt[i + 1] - ticksAt[i]) * frac) * scale;
	}

	private double distanceForMovingTicks(double ticks) {
		double target = ticks / scale;
		if (target >= ticksAt[ticksAt.length - 1]) {
			return length;
		}
		int lo = 0;
		int hi = ticksAt.length - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (ticksAt[mid] <= target) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		double span = ticksAt[hi] - ticksAt[lo];
		double f = span <= 0 ? 0 : (target - ticksAt[lo]) / span;
		double s0 = distanceOfSample(lo);
		double s1 = distanceOfSample(hi);
		return s0 + (s1 - s0) * f;
	}
}
