package io.github.firestormfmd.scenescripter.core.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.BlockBox;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * Moves a whole scene by whole blocks, for placing an imported scene somewhere else: positions, paths, bounds,
 * block events, look-at points, aim points and protect masks all move together.
 */
public final class SceneTransform {
	private SceneTransform() {
	}

	/** Moves the scene so its origin lands on {@code newOrigin}. */
	public static void moveOrigin(Scene scene, BlockPos newOrigin) {
		BlockPos o = scene.origin();
		translate(scene, newOrigin.x() - o.x(), newOrigin.y() - o.y(), newOrigin.z() - o.z());
	}

	public static void translate(Scene scene, int dx, int dy, int dz) {
		if (dx == 0 && dy == 0 && dz == 0) {
			return;
		}
		Vec3 d = new Vec3(dx, dy, dz);
		scene.setOrigin(scene.origin().offset(dx, dy, dz));
		if (scene.bounds() != null) {
			scene.setBounds(new BlockBox(scene.bounds().min().offset(dx, dy, dz), scene.bounds().max().offset(dx, dy, dz)));
		}
		for (MotionPath p : scene.paths()) {
			List<PathPoint> moved = new ArrayList<>();
			for (PathPoint pt : p.points()) {
				moved.add(pt.withPos(pt.pos().add(d)));
			}
			p.points().clear();
			p.points().addAll(moved);
		}
		List<SceneObject> owners = new ArrayList<>(scene.objects());
		owners.add(scene.tracks());
		for (SceneObject o : owners) {
			o.channel(BuiltInChannels.POSITION.name()).ifPresent(raw -> {
				Channel<Vec3> ch = o.channel(BuiltInChannels.POSITION);
				ch.setDefaultValue(ch.defaultValue().add(d));
				for (Keyframe<Vec3> k : List.copyOf(ch.keys())) {
					ch.put(new Keyframe<>(k.tick(), k.value().add(d), k.interpolation(), k.handles(), k.generatedBy()));
				}
			});
			o.channel(BuiltInChannels.LOOK_AT.name()).ifPresent(raw -> {
				Channel<String> ch = o.channel(BuiltInChannels.LOOK_AT);
				ch.setDefaultValue(movePoint(ch.defaultValue(), d));
				for (Keyframe<String> k : List.copyOf(ch.keys())) {
					ch.put(new Keyframe<>(k.tick(), movePoint(k.value(), d), k.interpolation(), k.handles(), k.generatedBy()));
				}
			});
			for (SceneEvent e : List.copyOf(o.events())) {
				Map<String, Object> params = new HashMap<>(e.params());
				boolean changed = false;
				if (params.get("x") instanceof Number x && params.get("y") instanceof Number y && params.get("z") instanceof Number z) {
					boolean whole = x.doubleValue() == Math.floor(x.doubleValue());
					params.put("x", whole ? (Object) (x.intValue() + dx) : x.doubleValue() + dx);
					params.put("y", whole ? (Object) (y.intValue() + dy) : y.doubleValue() + dy);
					params.put("z", whole ? (Object) (z.intValue() + dz) : z.doubleValue() + dz);
					changed = true;
				}
				if (params.get("at") instanceof String at) {
					params.put("at", movePoint(at, d));
					changed = true;
				}
				if (params.get("protect") instanceof String protect && !protect.isBlank()) {
					StringBuilder out = new StringBuilder();
					for (String part : protect.split(";")) {
						String moved = movePoint(part, d);
						if (!out.isEmpty()) {
							out.append("; ");
						}
						out.append(moved);
					}
					params.put("protect", out.toString());
					changed = true;
				}
				if (changed) {
					o.removeEvent(e.id());
					o.addEvent(new SceneEvent(e.id(), e.tick(), e.type(), e.target(), params, e.generatedBy()));
				}
			}
		}
	}

	/** Moves one object: its keyed positions, look-at points and aim points. Its paths stay where they are. */
	public static void moveObject(SceneObject o, Vec3 d) {
		o.channel(BuiltInChannels.POSITION.name()).ifPresent(raw -> {
			Channel<Vec3> ch = o.channel(BuiltInChannels.POSITION);
			ch.setDefaultValue(ch.defaultValue().add(d));
			for (Keyframe<Vec3> k : List.copyOf(ch.keys())) {
				ch.put(new Keyframe<>(k.tick(), k.value().add(d), k.interpolation(), k.handles(), k.generatedBy()));
			}
		});
		o.channel(BuiltInChannels.LOOK_AT.name()).ifPresent(raw -> {
			Channel<String> ch = o.channel(BuiltInChannels.LOOK_AT);
			ch.setDefaultValue(movePoint(ch.defaultValue(), d));
			for (Keyframe<String> k : List.copyOf(ch.keys())) {
				ch.put(new Keyframe<>(k.tick(), movePoint(k.value(), d), k.interpolation(), k.handles(), k.generatedBy()));
			}
		});
		for (SceneEvent e : List.copyOf(o.events())) {
			if (e.params().get("at") instanceof String at) {
				Map<String, Object> params = new HashMap<>(e.params());
				params.put("at", movePoint(at, d));
				o.removeEvent(e.id());
				o.addEvent(new SceneEvent(e.id(), e.tick(), e.type(), e.target(), params, e.generatedBy()));
			}
		}
	}

	/**
	 * Moves everything an object does in time by {@code delta} ticks: keyframes, events, motion clips and its
	 * lifetime. Nothing moves before tick 0.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void retimeObject(SceneObject o, int delta) {
		if (delta == 0) {
			return;
		}
		for (Channel ch : o.channels().values()) {
			List<Keyframe<?>> keys = List.copyOf(ch.keys());
			ch.removeIf(k -> true);
			// Later keys go in last, so if two land on tick 0 the later one wins, as it would have at the end.
			for (Keyframe<?> k : keys) {
				ch.putUnchecked(new Keyframe<>(Math.max(0, k.tick() + delta), k.value(), k.interpolation(), k.handles(),
						k.generatedBy()));
			}
		}
		for (SceneEvent e : List.copyOf(o.events())) {
			o.removeEvent(e.id());
			o.addEvent(e.withTick(Math.max(0, e.tick() + delta)));
		}
		for (MotionClip c : List.copyOf(o.motion())) {
			o.removeMotion(c);
			o.addMotion(c.shifted(Math.max(-c.startTick(), delta)));
		}
		int spawn = Math.max(0, o.spawnTick() + delta);
		int despawn = o.despawnTick() < 0 ? -1 : Math.max(spawn + 1, o.despawnTick() + delta);
		o.setLifetime(spawn, despawn);
	}

	/** Moves an "x y z" point; anything else (an object ID, empty) is left alone. */
	private static String movePoint(String value, Vec3 d) {
		String[] c = value.trim().split("[\\s,]+");
		if (c.length != 3) {
			return value;
		}
		try {
			double x = Double.parseDouble(c[0]) + d.x();
			double y = Double.parseDouble(c[1]) + d.y();
			double z = Double.parseDouble(c[2]) + d.z();
			return fmt(x) + " " + fmt(y) + " " + fmt(z);
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private static String fmt(double v) {
		return v == Math.floor(v) && Math.abs(v) < 1e9 ? Long.toString((long) v) : Double.toString(v);
	}
}
