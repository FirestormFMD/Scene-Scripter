package io.github.firestormfmd.scenescripter.core.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.path.LocomotionPlanner;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.ChannelSpec;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.core.scene.SpeedKey;

/**
 * Turns a take into animation: keyframes (every tick, or thinned to the few that keep the motion within a
 * tolerance), or a motion path with speed keys and jump, wait and gait markers so the path tools can edit it.
 * Applying a take replaces what the object did in the take's time range, like punching in on a recording.
 */
public final class TakeConverter {
	/** How far thinned positions may stray from the recording, in blocks. */
	public static final double POSITION_TOLERANCE = 0.05;
	/** How far thinned angles may stray from the recording, in degrees. */
	public static final double ANGLE_TOLERANCE = 2.0;
	/** Ticks standing still before a motion path gets a wait marker. */
	static final int MIN_WAIT = 10;
	/** Speed below which the performer counts as standing still, in blocks per tick. */
	static final double STILL = 0.02;

	/** Events a take records, replaced in the take's range when it is applied. */
	static final Set<String> CAPTURED_EVENTS = Set.of("attack", "swing", "place_block", "break_block", "use_block", "shoot");

	private static final List<ChannelSpec<String>> SLOTS = List.of(BuiltInChannels.MAINHAND, BuiltInChannels.OFFHAND,
			BuiltInChannels.HEAD, BuiltInChannels.CHEST, BuiltInChannels.LEGS, BuiltInChannels.FEET);

	public enum KeyMode {
		/** A keyframe every tick: exactly what was performed. */
		RAW,
		/** Only the keyframes needed to stay within the tolerances; much easier to edit. */
		THINNED
	}

	/** A motion path fitted to a take and the clip that plays it on the object. */
	public record PathFit(MotionPath path, MotionClip clip) {
	}

	private TakeConverter() {
	}

	/** Replaces the object's motion, pose and events in the take's range with the take, as keyframes. */
	public static void applyAsKeys(SceneObject object, Take take, KeyMode mode) {
		clearRange(object, take.startTick(), take.endTick(), true);
		List<CaptureSample> s = take.samples();
		int n = s.size();

		List<Integer> posKeys = mode == KeyMode.RAW ? all(n) : Rdp.simplify(s.stream().map(CaptureSample::pos).toList(),
				POSITION_TOLERANCE);
		Channel<Vec3> position = object.channel(BuiltInChannels.POSITION);
		for (int i : posKeys) {
			position.put(new Keyframe<>(s.get(i).tick(), s.get(i).pos(), Interpolation.LINEAR, null, null));
		}

		double[] body = unwrap(n, i -> (double) s.get(i).bodyYaw());
		keyAngles(object.channel(BuiltInChannels.BODY_YAW), s, body, mode);
		double[] head = new double[n];
		for (int i = 0; i < n; i++) {
			head[i] = LocomotionPlanner.wrapDegrees(s.get(i).headYaw() - s.get(i).bodyYaw());
		}
		keyAngles(object.channel(BuiltInChannels.HEAD_YAW), s, head, mode);
		double[] pitch = new double[n];
		for (int i = 0; i < n; i++) {
			pitch[i] = s.get(i).headPitch();
		}
		keyAngles(object.channel(BuiltInChannels.HEAD_PITCH), s, pitch, mode);

		keyStates(object, s);
		addEvents(object, take);
	}

	/**
	 * Fits a motion path to the take, or returns null if the performer hardly moved (keys suit that better).
	 *
	 * @param tolerance how far the path may stray from where the performer went, in blocks
	 */
	public static PathFit fitPath(Take take, String pathId, String name, double tolerance) {
		List<CaptureSample> s = take.samples();
		int n = s.size();
		double[] dist = new double[n];
		for (int i = 1; i < n; i++) {
			Vec3 d = s.get(i).pos().subtract(s.get(i - 1).pos());
			dist[i] = dist[i - 1] + d.horizontalLength();
		}
		double total = dist[n - 1];
		if (total < 1.0 || n < 3) {
			return null;
		}
		long grounded = s.stream().filter(CaptureSample::onGround).count();
		PathKind kind = grounded * 2 >= n ? PathKind.GROUND : PathKind.AIR;

		List<Vec3> flat = new ArrayList<>(n);
		for (CaptureSample c : s) {
			flat.add(kind == PathKind.GROUND ? c.pos().withY(0) : c.pos());
		}
		MotionPath path = new MotionPath(pathId, name, kind);
		double lastAt = -1;
		for (int i : Rdp.simplify(flat, tolerance)) {
			if (i > 0 && i < n - 1 && dist[i] - lastAt < 0.25) {
				continue;
			}
			path.points().add(PathPoint.at(s.get(i).pos()));
			lastAt = dist[i];
		}
		if (path.points().size() < 2) {
			return null;
		}

		List<PathMarker> markers = new ArrayList<>();
		// Standing still becomes a wait marker.
		int runStart = -1;
		for (int i = 1; i <= n; i++) {
			boolean still = i < n && dist[i] - dist[i - 1] < STILL;
			if (still && runStart < 0) {
				runStart = i - 1;
			} else if (!still && runStart >= 0) {
				int ticks = i - 1 - runStart;
				if (ticks >= MIN_WAIT && dist[runStart] > 0 && dist[runStart] < total) {
					markers.add(PathMarker.waitFor(dist[runStart] / total, ticks));
				}
				runStart = -1;
			}
		}
		// Leaving the ground while rising is a jump.
		if (kind == PathKind.GROUND) {
			for (int i = 1; i + 1 < n; i++) {
				if (s.get(i - 1).onGround() && !s.get(i).onGround() && s.get(i + 1).pos().y() > s.get(i - 1).pos().y() + 0.2) {
					markers.add(PathMarker.jump(dist[i] / total));
				}
			}
		}
		// Changes between walking, sprinting and sneaking become gait markers.
		Gait gait = gait(s.getFirst());
		path.setGait(gait);
		for (int i = 1; i < n; i++) {
			Gait g = gait(s.get(i));
			if (g != gait && dist[i] > 0 && dist[i] < total) {
				markers.add(PathMarker.gait(dist[i] / total, g));
				gait = g;
			}
		}
		path.setMarkers(markers);

		// Speed keys every half second of movement.
		List<SpeedKey> speeds = new ArrayList<>();
		double sum = 0;
		int windows = 0;
		for (int a = 0; a + 1 < n; a += 10) {
			int b = Math.min(n - 1, a + 10);
			double moved = dist[b] - dist[a];
			if (moved / (b - a) < STILL) {
				continue;
			}
			double speed = moved / ((b - a) / 20.0);
			double at = Math.clamp((dist[a] + dist[b]) / 2 / total, 0, 1);
			speeds.add(new SpeedKey(at, speed));
			sum += speed;
			windows++;
		}
		if (windows > 0) {
			path.setBaseSpeed(sum / windows);
		}
		path.setSpeedKeys(speeds);
		return new PathFit(path, MotionClip.fitted(pathId, take.startTick(), take.endTick()));
	}

	/**
	 * Replaces the object's motion in the take's range with a motion path fitted to it; the head, pose, equipment
	 * and events still come from the take as keys. The caller adds the returned path to the scene.
	 *
	 * @return the fit, or null if the take hardly moves (then it is applied as keys instead)
	 */
	public static PathFit applyAsPath(SceneObject object, Take take, String pathId, String name, double tolerance) {
		PathFit fit = fitPath(take, pathId, name, tolerance);
		if (fit == null) {
			applyAsKeys(object, take, KeyMode.THINNED);
			return null;
		}
		clearRange(object, take.startTick(), take.endTick(), true);
		object.addMotion(fit.clip());
		List<CaptureSample> s = take.samples();
		int n = s.size();
		double[] head = new double[n];
		double[] pitch = new double[n];
		for (int i = 0; i < n; i++) {
			head[i] = LocomotionPlanner.wrapDegrees(s.get(i).headYaw() - s.get(i).bodyYaw());
			pitch[i] = s.get(i).headPitch();
		}
		keyAngles(object.channel(BuiltInChannels.HEAD_YAW), s, head, KeyMode.THINNED);
		keyAngles(object.channel(BuiltInChannels.HEAD_PITCH), s, pitch, KeyMode.THINNED);
		keyStates(object, s);
		addEvents(object, take);
		return fit;
	}

	/** Removes what the object did between two ticks: keys of recorded channels, clips starting there, and events. */
	static void clearRange(SceneObject o, int start, int end, boolean clips) {
		List<String> channels = new ArrayList<>(List.of(BuiltInChannels.POSITION.name(), BuiltInChannels.BODY_YAW.name(),
				BuiltInChannels.HEAD_YAW.name(), BuiltInChannels.HEAD_PITCH.name(), BuiltInChannels.SNEAKING.name(),
				BuiltInChannels.SPRINTING.name(), BuiltInChannels.POSE.name()));
		SLOTS.forEach(slot -> channels.add(slot.name()));
		for (String name : channels) {
			o.channel(name).ifPresent(ch -> ch.removeIf(k -> k.tick() >= start && k.tick() <= end));
		}
		if (clips) {
			for (MotionClip c : List.copyOf(o.motion())) {
				if (c.startTick() >= start && c.startTick() <= end) {
					o.removeMotion(c);
				}
			}
		}
		for (SceneEvent e : List.copyOf(o.events())) {
			if (!e.isGenerated() && CAPTURED_EVENTS.contains(e.type()) && e.tick() >= start && e.tick() <= end) {
				o.removeEvent(e.id());
			}
		}
	}

	private static void keyAngles(Channel<Double> channel, List<CaptureSample> s, double[] values, KeyMode mode) {
		List<Integer> keep = mode == KeyMode.RAW ? all(values.length)
				: Rdp.simplify(values.length, i -> values[i], ANGLE_TOLERANCE);
		for (int i : keep) {
			channel.put(new Keyframe<>(s.get(i).tick(), values[i], Interpolation.LINEAR, null, null));
		}
	}

	/** Sneaking, sprinting, pose and equipment: a key at the start and wherever they change. */
	private static void keyStates(SceneObject o, List<CaptureSample> s) {
		keyChanges(o.channel(BuiltInChannels.SNEAKING), s, CaptureSample::sneaking);
		keyChanges(o.channel(BuiltInChannels.SPRINTING), s, CaptureSample::sprinting);
		keyChanges(o.channel(BuiltInChannels.POSE), s, c -> c.swimming() ? "swimming" : c.pose());
		for (ChannelSpec<String> slot : SLOTS) {
			String key = slot.name().substring("equipment.".length());
			keyChanges(o.channel(slot), s, c -> c.equipment().getOrDefault(key, ""));
		}
	}

	private static <T> void keyChanges(Channel<T> channel, List<CaptureSample> s, Function<CaptureSample, T> value) {
		T last = null;
		for (int i = 0; i < s.size(); i++) {
			T v = value.apply(s.get(i));
			if (i == 0 || !v.equals(last)) {
				channel.put(new Keyframe<>(s.get(i).tick(), v, Interpolation.STEP, null, null));
			}
			last = v;
		}
	}

	private static void addEvents(SceneObject o, Take take) {
		for (SceneEvent e : take.events()) {
			if (o.findEvent(e.id()).isEmpty()) {
				o.addEvent(e);
			}
		}
	}

	/** Angles made continuous, so a turn through north does not spin the long way round when interpolated. */
	private static double[] unwrap(int n, java.util.function.IntFunction<Double> angle) {
		double[] out = new double[n];
		for (int i = 0; i < n; i++) {
			double a = angle.apply(i);
			out[i] = i == 0 ? a : out[i - 1] + LocomotionPlanner.wrapDegrees(a - out[i - 1]);
		}
		return out;
	}

	private static Gait gait(CaptureSample c) {
		return c.sneaking() ? Gait.SNEAK : c.sprinting() ? Gait.SPRINT : Gait.WALK;
	}

	private static List<Integer> all(int n) {
		List<Integer> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(i);
		}
		return out;
	}
}
