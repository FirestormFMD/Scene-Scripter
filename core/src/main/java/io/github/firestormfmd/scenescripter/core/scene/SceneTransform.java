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
