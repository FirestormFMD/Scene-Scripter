package io.github.firestormfmd.scenescripter.core.runtime;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.path.BodySettings;
import io.github.firestormfmd.scenescripter.core.path.Locomotion;
import io.github.firestormfmd.scenescripter.core.path.LocomotionPlanner;
import io.github.firestormfmd.scenescripter.core.path.MotionSample;
import io.github.firestormfmd.scenescripter.core.path.TerrainView;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.ChannelSpec;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Works out every object's state at any tick. This is a pure function of the scene (plus the terrain paths snap
 * to), so scrubbing to a tick always gives the same result as playing up to it.
 *
 * <p>Position and body facing combine motion clips with keyframes. While a clip runs it decides; outside clips the
 * object moves between the surrounding anchors, where an anchor is a keyframe or the start or end of a clip.
 * Between two keyframes the keyframe's interpolation applies; next to a clip the object moves in a straight line.
 *
 * <p>The head is keyed relative to the body: {@code head_yaw} 0 looks straight ahead. {@code look_at}, when set,
 * turns the head towards an object or a point instead.
 *
 * <p>Planned locomotion is cached. Call {@link #invalidate()} after editing the scene or changing the terrain.
 */
public final class SceneEvaluator {
	/** How far the head can turn from the body, in degrees, like a real mob's neck. */
	private static final float MAX_HEAD_TURN = 75;
	private static final int MAX_LOOK_AT_DEPTH = 4;

	private final Scene scene;
	private final BodyProvider bodies;
	private TerrainView terrain;
	private final Map<String, List<Locomotion>> locomotion = new HashMap<>();

	/**
	 * @param terrain blocks ground paths snap to; may be null if the scene has no ground paths
	 */
	public SceneEvaluator(Scene scene, TerrainView terrain, BodyProvider bodies) {
		this.scene = scene;
		this.terrain = terrain;
		this.bodies = bodies;
	}

	public Scene scene() {
		return scene;
	}

	public void setTerrain(TerrainView terrain) {
		this.terrain = terrain;
		invalidate();
	}

	/** Forgets planned motion; the next evaluation re-plans it. */
	public void invalidate() {
		locomotion.clear();
	}

	public void invalidate(String objectId) {
		locomotion.remove(objectId);
	}

	/** Planned motion for each of an object's clips, in clip order. Clips whose path is missing are skipped. */
	public List<Locomotion> locomotion(SceneObject object) {
		return locomotion.computeIfAbsent(object.id(), id -> plan(object));
	}

	private List<Locomotion> plan(SceneObject object) {
		BodySettings body = bodies.bodyFor(object);
		return object.motion().stream()
				.map(clip -> planClip(clip, body))
				.flatMap(Optional::stream)
				.toList();
	}

	private Optional<Locomotion> planClip(MotionClip clip, BodySettings body) {
		Optional<MotionPath> path = scene.path(clip.pathId());
		if (path.isEmpty() || path.get().points().isEmpty()) {
			return Optional.empty();
		}
		if (path.get().kind() == PathKind.GROUND && terrain == null) {
			return Optional.empty();
		}
		return Optional.of(LocomotionPlanner.plan(path.get(), clip, body, terrain, scene.settings().groundFilter()));
	}

	public Optional<ObjectState> evaluate(String objectId, int tick) {
		return scene.object(objectId).map(o -> evaluate(o, tick));
	}

	public ObjectState evaluate(SceneObject o, int tick) {
		return evaluate(o, tick, 0);
	}

	private ObjectState evaluate(SceneObject o, int tick, int depth) {
		List<Locomotion> clips = locomotion(o);
		Locomotion active = null;
		for (Locomotion l : clips) {
			if (tick >= l.startTick() && tick <= l.endTick()) {
				active = l;
			}
		}

		Vec3 position;
		float bodyYaw;
		boolean onGround = true;
		boolean moving = false;
		boolean swimming = false;
		Gait gait = Gait.WALK;
		if (active != null) {
			MotionSample s = active.sampleAt(tick);
			position = s.pos();
			bodyYaw = s.yaw();
			onGround = s.onGround();
			moving = s.moving();
			swimming = s.swimming();
			gait = s.gait();
		} else {
			position = anchoredPosition(o, clips, tick);
			bodyYaw = anchoredYaw(o, clips, tick);
		}

		position = position.add(value(o, BuiltInChannels.OFFSET, tick));

		float headYaw = bodyYaw + (float) (double) value(o, BuiltInChannels.HEAD_YAW, tick);
		float headPitch = (float) (double) value(o, BuiltInChannels.HEAD_PITCH, tick);
		String lookAt = value(o, BuiltInChannels.LOOK_AT, tick);
		if (!lookAt.isBlank() && depth < MAX_LOOK_AT_DEPTH) {
			Optional<Vec3> target = lookTarget(lookAt, tick, depth);
			if (target.isPresent()) {
				Vec3 eye = position.add(0, bodies.bodyFor(o).height() * 0.85, 0);
				Vec3 d = target.get().subtract(eye);
				if (d.horizontalLength() > 1.0e-6) {
					float wanted = d.yaw();
					float rel = (float) LocomotionPlanner.wrapDegrees(wanted - bodyYaw);
					headYaw = bodyYaw + Math.clamp(rel, -MAX_HEAD_TURN, MAX_HEAD_TURN);
				}
				headPitch = (float) -Math.toDegrees(Math.atan2(d.y(), d.horizontalLength()));
			}
		}

		boolean sneaking = value(o, BuiltInChannels.SNEAKING, tick) || (active != null && gait == Gait.SNEAK);
		boolean sprinting = value(o, BuiltInChannels.SPRINTING, tick) || (active != null && moving && gait == Gait.SPRINT);
		swimming = swimming || (active != null && moving && gait == Gait.SWIM);

		Map<String, String> equipment = new LinkedHashMap<>();
		for (ChannelSpec<String> slot : List.of(BuiltInChannels.MAINHAND, BuiltInChannels.OFFHAND, BuiltInChannels.HEAD,
				BuiltInChannels.CHEST, BuiltInChannels.LEGS, BuiltInChannels.FEET)) {
			equipment.put(slot.name().substring("equipment.".length()), value(o, slot, tick));
		}

		boolean dead = value(o, BuiltInChannels.DEAD, tick);

		Map<String, Object> extra = new LinkedHashMap<>();
		o.channels().forEach((name, ch) -> {
			if (BuiltInChannels.byName(name).isEmpty()) {
				extra.put(name, ch.valueAt(tick));
			}
		});

		return new ObjectState(
				o.id(),
				o.existsAt(tick),
				position,
				bodyYaw,
				headYaw,
				headPitch,
				onGround,
				moving,
				swimming,
				gait,
				value(o, BuiltInChannels.POSE, tick),
				sneaking,
				sprinting,
				value(o, BuiltInChannels.ON_FIRE, tick),
				value(o, BuiltInChannels.GLOWING, tick),
				value(o, BuiltInChannels.INVISIBLE, tick),
				value(o, BuiltInChannels.SCALE, tick),
				value(o, BuiltInChannels.CUSTOM_NAME, tick),
				value(o, BuiltInChannels.NAME_VISIBLE, tick),
				equipment,
				value(o, BuiltInChannels.HEALTH, tick),
				dead,
				dead ? ticksSinceBecameTrue(o, BuiltInChannels.DEAD, tick) : -1,
				value(o, BuiltInChannels.AMBIENT_SOUNDS, tick),
				value(o, BuiltInChannels.SILENT, tick),
				extra);
	}

	private Optional<Vec3> lookTarget(String lookAt, int tick, int depth) {
		String[] parts = lookAt.trim().split("\\s+");
		if (parts.length == 3) {
			try {
				return Optional.of(new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
						Double.parseDouble(parts[2])));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		}
		return scene.object(lookAt.trim()).map(target -> {
			ObjectState s = evaluate(target, tick, depth + 1);
			return s.position().add(0, bodies.bodyFor(target).height() * 0.85, 0);
		});
	}

	/** Channel value, or the spec's default when the object has no such channel. */
	private static <T> T value(SceneObject o, ChannelSpec<T> spec, int tick) {
		Optional<Channel<?>> ch = o.channel(spec.name());
		if (ch.isEmpty()) {
			return spec.defaultValue();
		}
		return spec.type().cast(ch.get().valueAt(tick));
	}

	/** How many ticks a boolean channel has been continuously true at {@code tick}, counting from when it turned on. */
	@SuppressWarnings("unchecked")
	private static int ticksSinceBecameTrue(SceneObject o, ChannelSpec<Boolean> spec, int tick) {
		final int always = Integer.MAX_VALUE / 2;
		Optional<Channel<?>> ch = o.channel(spec.name());
		if (ch.isEmpty()) {
			return always;
		}
		Channel<Boolean> c = (Channel<Boolean>) ch.get();
		boolean state = c.defaultValue();
		long since = state ? Long.MIN_VALUE : -1;
		for (Keyframe<Boolean> k : c.keys()) {
			if (k.tick() > tick) {
				break;
			}
			if (k.value() && !state) {
				since = k.tick();
			}
			state = k.value();
		}
		if (!state) {
			return -1;
		}
		return since == Long.MIN_VALUE ? always : (int) (tick - since);
	}

	// ---- Anchored position and facing outside motion clips ----

	private record Anchor<T>(int tick, T value, boolean fromKey, Interpolation interpolation) {
	}

	private static Vec3 anchoredPosition(SceneObject o, List<Locomotion> clips, int tick) {
		Channel<Vec3> ch = o.channel(BuiltInChannels.POSITION.name()).isPresent()
				? o.channel(BuiltInChannels.POSITION)
				: null;
		Anchor<Vec3> prev = null;
		Anchor<Vec3> next = null;
		if (ch != null) {
			for (Keyframe<Vec3> k : ch.keys()) {
				Anchor<Vec3> a = new Anchor<>(k.tick(), k.value(), true, k.interpolation());
				if (k.tick() <= tick) {
					prev = a;
				} else if (next == null) {
					next = a;
				}
			}
		}
		for (Locomotion l : clips) {
			if (l.endTick() < tick && (prev == null || l.endTick() >= prev.tick)) {
				prev = new Anchor<>(l.endTick(), l.samples().getLast().pos(), false, Interpolation.LINEAR);
			}
			if (l.startTick() > tick && (next == null || l.startTick() < next.tick)) {
				next = new Anchor<>(l.startTick(), l.samples().getFirst().pos(), false, Interpolation.LINEAR);
			}
		}
		if (prev == null && next == null) {
			return ch != null ? ch.defaultValue() : BuiltInChannels.POSITION.defaultValue();
		}
		if (prev == null) {
			return next.value;
		}
		if (next == null) {
			return prev.value;
		}
		if (prev.fromKey && next.fromKey) {
			return ch.valueAt(tick);
		}
		if (prev.fromKey && prev.interpolation == Interpolation.STEP) {
			return prev.value;
		}
		double f = (double) (tick - prev.tick) / (next.tick - prev.tick);
		return Vec3.lerp(prev.value, next.value, f);
	}

	private static float anchoredYaw(SceneObject o, List<Locomotion> clips, int tick) {
		Channel<Double> ch = o.channel(BuiltInChannels.BODY_YAW.name()).isPresent()
				? o.channel(BuiltInChannels.BODY_YAW)
				: null;
		Anchor<Float> prev = null;
		Anchor<Float> next = null;
		if (ch != null) {
			for (Keyframe<Double> k : ch.keys()) {
				Anchor<Float> a = new Anchor<>(k.tick(), (float) (double) k.value(), true, k.interpolation());
				if (k.tick() <= tick) {
					prev = a;
				} else if (next == null) {
					next = a;
				}
			}
		}
		for (Locomotion l : clips) {
			if (l.endTick() < tick && (prev == null || l.endTick() >= prev.tick)) {
				prev = new Anchor<>(l.endTick(), l.samples().getLast().yaw(), false, Interpolation.LINEAR);
			}
			if (l.startTick() > tick && (next == null || l.startTick() < next.tick)) {
				next = new Anchor<>(l.startTick(), l.samples().getFirst().yaw(), false, Interpolation.LINEAR);
			}
		}
		if (prev == null && next == null) {
			return ch != null ? (float) (double) ch.defaultValue() : 0f;
		}
		if (prev == null) {
			return next.value;
		}
		if (next == null) {
			return prev.value;
		}
		if (prev.fromKey && next.fromKey) {
			return (float) (double) ch.valueAt(tick);
		}
		if (prev.fromKey && prev.interpolation == Interpolation.STEP) {
			return prev.value;
		}
		double f = (double) (tick - prev.tick) / (next.tick - prev.tick);
		return (float) (prev.value + LocomotionPlanner.wrapDegrees(next.value - prev.value) * f);
	}
}
