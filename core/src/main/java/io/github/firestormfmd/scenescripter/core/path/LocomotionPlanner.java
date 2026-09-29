package io.github.firestormfmd.scenescripter.core.path;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.GroundFilter;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.TimingMode;

/**
 * Plans how an object moves along a motion path, tick by tick. Horizontal position comes from the path and its
 * timing; height comes from the ground, with vanilla stepping, jumping and falling:
 * <ul>
 * <li>rises up to the step height are walked up,</li>
 * <li>higher rises are jumped, taking off as late as still clears the rise,</li>
 * <li>drops are fallen with vanilla gravity,</li>
 * <li>rises higher than the jump are reported, and the object pops up onto them.</li>
 * </ul>
 * The result is deterministic, so scrubbing and replaying always give the same motion.
 */
public final class LocomotionPlanner {
	/** How far ahead, in ticks, to look for a rise that needs a jump. */
	private static final int LOOKAHEAD = 24;
	/** Longest jump simulated when testing whether a takeoff clears a rise. */
	private static final int MAX_AIR_TICKS = 60;
	private static final double EPS = 1.0e-6;

	private final MotionPath path;
	private final MotionClip clip;
	private final BodySettings body;
	private final GroundProbe probe;
	/** Set when the terrain changes over time, so each probe sees the blocks as they are at its tick. */
	private final TerrainTimeline timeline;
	private final PathGeometry geometry;
	private final TimingProfile timing;
	private final int ticks;
	private final double apex;

	private double[] distance;
	private GroundProbe.Hit[] ground;

	private LocomotionPlanner(MotionPath path, MotionClip clip, BodySettings body, TerrainView terrain,
			GroundFilter filter) {
		this.path = path;
		this.clip = clip;
		this.body = path.jumpHeight() != null ? body.withJumpHeight(path.jumpHeight()) : body;
		this.probe = terrain == null ? null : new GroundProbe(terrain, filter, this.body);
		this.timeline = terrain instanceof TerrainTimeline t ? t : null;
		this.geometry = PathGeometry.of(path, clip.lateralOffset());
		this.timing = new TimingProfile(path, clip, geometry.length());
		this.ticks = clip.timing() == TimingMode.FIT
				? clip.endTick() - clip.startTick()
				: (int) Math.ceil(timing.duration() - EPS);
		this.apex = JumpPhysics.apexHeight(this.body.jumpVelocity());
	}

	/**
	 * @param terrain blocks to snap onto; may be null for air paths
	 */
	public static Locomotion plan(MotionPath path, MotionClip clip, BodySettings body, TerrainView terrain,
			GroundFilter filter) {
		if (path.kind() == PathKind.GROUND && terrain == null) {
			throw new IllegalArgumentException("Ground paths need terrain to snap onto");
		}
		return new LocomotionPlanner(path, clip, body, terrain, filter).run();
	}

	private Locomotion run() {
		int total = ticks + 1 + LOOKAHEAD;
		distance = new double[total];
		for (int i = 0; i < total; i++) {
			distance[i] = timing.distanceAt(i);
		}

		List<MotionSample> samples = new ArrayList<>(ticks + 1);
		List<PathIssue> issues = new ArrayList<>();
		List<Integer> jumps = new ArrayList<>();
		List<Locomotion.Landing> landings = new ArrayList<>();
		Map<PathIssue.Kind, Integer> lastIssueTick = new EnumMap<>(PathIssue.Kind.class);

		if (timing.fitFailed()) {
			issues.add(new PathIssue(PathIssue.Kind.NOT_ENOUGH_TIME, clip.startTick(), 0));
		}

		List<PathMarker> markers = path.markers();
		Gait gait = path.gait();
		float yaw = initialYaw();

		if (path.kind() == PathKind.AIR) {
			for (int i = 0; i <= ticks; i++) {
				double s = distance[i];
				double prevS = i > 0 ? distance[i - 1] : 0;
				gait = gaitAfter(markers, gait, prevS, s, i == 0);
				yaw = turn(yaw, s, i > 0 && s > prevS + EPS);
				samples.add(new MotionSample(geometry.pointAt(s), yaw, false, i > 0 && s > prevS + EPS, false, gait));
			}
			return new Locomotion(clip.startTick(), samples, issues, jumps, landings);
		}

		probeGround(total);

		double y = ground[0].found() ? ground[0].y() : geometry.pointAt(0).y();
		double vy = 0;
		double peakY = y;
		boolean airborne = false;

		for (int i = 0; i <= ticks; i++) {
			int tick = clip.startTick() + i;
			double s = distance[i];
			double prevS = i > 0 ? distance[i - 1] : 0;
			boolean moving = i > 0 && s > prevS + EPS;
			Vec3 pos = geometry.pointAt(s);
			gait = gaitAfter(markers, gait, prevS, s, i == 0);

			GroundProbe.Hit hit = ground[i];
			double g;
			if (hit.found()) {
				g = hit.y();
			} else {
				report(issues, lastIssueTick, hit.kind() == GroundProbe.Kind.BLOCKED
						? PathIssue.Kind.FLUID_BLOCKED : PathIssue.Kind.NO_GROUND, tick, s);
				g = y;
			}
			boolean swimming = hit.kind() == GroundProbe.Kind.FLUID;

			if (i > 0 && !airborne) {
				boolean markerJump = moving && crossesJumpMarker(markers, prevS, s);
				if (!swimming && (markerJump || shouldJumpNow(i, y))) {
					airborne = true;
					vy = body.jumpVelocity();
					peakY = y;
					jumps.add(tick);
				} else if (g >= y - EPS) {
					if (g - y > body.stepHeight() + EPS) {
						report(issues, lastIssueTick, g - y > apex + EPS
								? PathIssue.Kind.TOO_HIGH : PathIssue.Kind.JUMP_BLOCKED, tick, s);
					}
					y = g;
				} else if (y - g < 1.0e-3 || (swimming && y - g <= body.stepHeight())) {
					y = g;
				} else {
					airborne = true;
					vy = JumpPhysics.EDGE_FALL_VELOCITY;
					peakY = y;
				}
			} else if (i == 0) {
				y = g;
			}

			if (airborne) {
				double ny = y + vy;
				vy = JumpPhysics.nextVelocity(vy);
				peakY = Math.max(peakY, ny);
				if (ny <= g) {
					if (y < g - body.stepHeight() - EPS) {
						// Flew into the side of a block rather than landing on it.
						report(issues, lastIssueTick, g - y > apex + EPS
								? PathIssue.Kind.TOO_HIGH : PathIssue.Kind.JUMP_BLOCKED, tick, s);
					}
					landings.add(new Locomotion.Landing(tick, Math.max(0, peakY - g)));
					if (path.maxDrop() != null && peakY - g > path.maxDrop() + EPS) {
						report(issues, lastIssueTick, PathIssue.Kind.LONG_DROP, tick, s);
					}
					airborne = false;
					vy = 0;
					y = g;
				} else {
					y = ny;
				}
			}

			yaw = turn(yaw, s, moving);
			samples.add(new MotionSample(new Vec3(pos.x(), y, pos.z()), yaw, !airborne, moving, swimming, gait));
		}

		// A clip that ends mid-jump or mid-fall keeps falling in place until it lands, rather than hanging in the air.
		Vec3 end = samples.getLast().pos();
		double endGround = groundY(ticks + 1, y);
		for (int extra = 1; airborne && extra <= MAX_AIR_TICKS; extra++) {
			double ny = y + vy;
			vy = JumpPhysics.nextVelocity(vy);
			peakY = Math.max(peakY, ny);
			int tick = clip.startTick() + ticks + extra;
			if (ny <= endGround) {
				landings.add(new Locomotion.Landing(tick, Math.max(0, peakY - endGround)));
				if (path.maxDrop() != null && peakY - endGround > path.maxDrop() + EPS) {
					report(issues, lastIssueTick, PathIssue.Kind.LONG_DROP, tick, distance[ticks]);
				}
				airborne = false;
				y = endGround;
			} else {
				y = ny;
			}
			samples.add(new MotionSample(new Vec3(end.x(), y, end.z()), yaw, !airborne, false, false, gait));
		}
		return new Locomotion(clip.startTick(), samples, issues, jumps, landings);
	}

	/** Finds the ground at every tick's position, searching near the ground found on the tick before. */
	private void probeGround(int total) {
		ground = new GroundProbe.Hit[total];
		double nearY = geometry.pointAt(0).y();
		for (int i = 0; i < total; i++) {
			if (timeline != null) {
				timeline.setTick(clip.startTick() + i);
			}
			Vec3 p = geometry.pointAt(distance[i]);
			GroundProbe.Hit hit = probe.groundAt(p.x(), p.z(), nearY);
			if (!hit.found() && i == 0) {
				// The first point was clicked on the ground, so try again from just above it.
				hit = probe.groundAt(p.x(), p.z(), p.y() + 0.5);
			}
			ground[i] = hit;
			if (hit.found()) {
				nearY = hit.y();
			}
		}
	}

	private double groundY(int i, double fallback) {
		GroundProbe.Hit h = ground[Math.min(i, ground.length - 1)];
		return h.found() ? h.y() : fallback;
	}

	/**
	 * Whether to take off on tick {@code i} for a rise coming up. Jumps as late as possible so the takeoff looks
	 * like a natural hop onto the block rather than an early leap.
	 */
	private boolean shouldJumpNow(int i, double y) {
		int rise = -1;
		for (int k = 1; k <= LOOKAHEAD && i + k < ground.length; k++) {
			double gk = groundY(i + k, y);
			if (gk > y + body.stepHeight() + EPS) {
				if (gk - y > apex + EPS) {
					// Too high to ever clear; the object walks into it and the issue is reported there.
					return false;
				}
				rise = i + k;
				break;
			}
		}
		if (rise < 0) {
			return false;
		}
		if (rise > i + 1 && clears(i + 1, y, rise)) {
			return false;
		}
		return clears(i, y, rise) || rise == i + 1;
	}

	/**
	 * Whether a jump taking off on tick {@code start} from height {@code y} lands on or beyond tick {@code rise}
	 * without touching the side of a block on the way up. Like vanilla, a falling body may still step onto a
	 * ledge up to the step height above it.
	 */
	private boolean clears(int start, double y, int rise) {
		double h = y;
		double v = body.jumpVelocity();
		for (int m = start; m < start + MAX_AIR_TICKS && m < ground.length; m++) {
			double prev = h;
			boolean rising = v > 0;
			h += v;
			v = JumpPhysics.nextVelocity(v);
			double gm = groundY(m, y);
			if (h <= gm) {
				if (rising && h < gm - EPS) {
					return false;
				}
				return prev >= gm - body.stepHeight() - EPS && m >= rise;
			}
		}
		return false;
	}

	private boolean crossesJumpMarker(List<PathMarker> markers, double prevS, double s) {
		double length = geometry.length();
		for (PathMarker m : markers) {
			double at = m.at() * length;
			if (m.kind() == PathMarker.Kind.JUMP && at > prevS + EPS && at <= s + EPS) {
				return true;
			}
		}
		return false;
	}

	private Gait gaitAfter(List<PathMarker> markers, Gait current, double prevS, double s, boolean first) {
		double length = geometry.length();
		Gait g = current;
		for (PathMarker m : markers) {
			if (m.kind() != PathMarker.Kind.GAIT) {
				continue;
			}
			double at = m.at() * length;
			if ((first && at <= s + EPS) || (at > prevS + EPS && at <= s + EPS)) {
				g = m.gait();
			}
		}
		return g;
	}

	private float initialYaw() {
		Vec3 dir = geometry.directionAt(0);
		return dir.horizontalLength() < EPS ? 0 : dir.yaw();
	}

	private float turn(float yaw, double s, boolean moving) {
		if (!moving) {
			return yaw;
		}
		Vec3 dir = geometry.directionAt(s);
		if (dir.horizontalLength() < EPS) {
			return yaw;
		}
		double diff = wrapDegrees(dir.yaw() - yaw);
		double step = Math.clamp(diff, -body.turnRate(), body.turnRate());
		return (float) wrapDegrees(yaw + step);
	}

	private static void report(List<PathIssue> issues, Map<PathIssue.Kind, Integer> lastTick, PathIssue.Kind kind,
			int tick, double s) {
		Integer last = lastTick.put(kind, tick);
		if (last == null || last != tick - 1) {
			issues.add(new PathIssue(kind, tick, s));
		}
	}

	/** Wraps an angle into [-180, 180). */
	public static double wrapDegrees(double degrees) {
		double d = degrees % 360;
		if (d >= 180) {
			d -= 360;
		}
		if (d < -180) {
			d += 360;
		}
		return d;
	}
}
