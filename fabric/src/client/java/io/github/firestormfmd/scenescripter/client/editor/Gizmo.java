package io.github.firestormfmd.scenescripter.client.editor;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import io.github.firestormfmd.scenescripter.client.ClientScene;

/**
 * Move and turn handles on the selected actor: arrows along X, Y and Z, and a ring on the ground for turning.
 * Dragging a handle previews the change; letting go keys it, snapped like other drags.
 */
public final class Gizmo {
	public static final int NONE = 0;
	public static final int X = 1;
	public static final int Y = 2;
	public static final int Z = 3;
	public static final int YAW = 4;
	/** Radius of the turning ring, outside the arrows so the two never overlap. */
	public static final double RING = 2.2;
	private static final double LIFT = 0.05;

	/** The handle under the mouse, or being dragged. */
	public static int hovered;
	public static int dragging;
	/** Where the actor was, and the handle's reading, when the drag started. */
	private static io.github.firestormfmd.scenescripter.core.math.Vec3 start;
	private static double grab;
	private static double startYaw;
	/** What letting go would key: a position for the arrows, a body yaw for the ring. */
	public static io.github.firestormfmd.scenescripter.core.math.@Nullable Vec3 previewPos;
	public static @Nullable Double previewYaw;

	private Gizmo() {
	}

	/** The selected actor, when the select tool shows handles on it. */
	public static @Nullable Entity target() {
		if (EditorState.tool != EditorState.Tool.SELECT || EditorState.selectedObject == null
				|| EditorState.locked.contains(EditorState.selectedObject)) {
			return null;
		}
		return ClientScene.actor(EditorState.selectedObject).orElse(null);
	}

	/** The two ends of a move arrow for an actor standing at {@code o}. */
	public static Vec3[] arrow(int axis, Vec3 o, double height) {
		return switch (axis) {
			case X -> new Vec3[] {o.add(0.5, LIFT, 0), o.add(1.8, LIFT, 0)};
			case Z -> new Vec3[] {o.add(0, LIFT, 0.5), o.add(0, LIFT, 1.8)};
			default -> new Vec3[] {o.add(0, height + 0.2, 0), o.add(0, height + 1.5, 0)};
		};
	}

	private static Vec3 direction(int axis) {
		return switch (axis) {
			case X -> new Vec3(1, 0, 0);
			case Y -> new Vec3(0, 1, 0);
			default -> new Vec3(0, 0, 1);
		};
	}

	/** The handle a ray points at, or {@link #NONE}. */
	public static int pick(Picking.Ray ray) {
		Entity e = target();
		if (e == null) {
			return NONE;
		}
		Vec3 o = e.position();
		double tolerance = Math.max(0.12, 0.03 * ray.from().distanceTo(o));
		int best = NONE;
		double bestDistance = tolerance;
		for (int axis : new int[] {X, Y, Z}) {
			Vec3[] ends = arrow(axis, o, e.getBbHeight());
			double d = rayToSegment(ray, ends[0], ends[1]);
			if (d < bestDistance) {
				bestDistance = d;
				best = axis;
			}
		}
		if (best != NONE) {
			return best;
		}
		Vec3 onRing = onGround(ray, o);
		if (onRing != null && Math.abs(Math.hypot(onRing.x - o.x, onRing.z - o.z) - RING) < tolerance * 1.5) {
			return YAW;
		}
		return NONE;
	}

	/** Starts dragging a handle; false if the ray misses them all. */
	public static boolean begin(Picking.Ray ray, double bodyYaw) {
		int handle = pick(ray);
		Entity e = target();
		if (handle == NONE || e == null) {
			return false;
		}
		Vec3 o = e.position();
		start = new io.github.firestormfmd.scenescripter.core.math.Vec3(o.x, o.y, o.z);
		startYaw = bodyYaw;
		if (handle == YAW) {
			Vec3 p = onGround(ray, o);
			if (p == null) {
				return false;
			}
			grab = yawTowards(o, p);
		} else {
			grab = along(ray, o, direction(handle));
		}
		dragging = handle;
		previewPos = null;
		previewYaw = null;
		return true;
	}

	/** Follows the mouse while dragging. */
	public static void drag(Picking.Ray ray) {
		if (dragging == NONE) {
			return;
		}
		Vec3 o = new Vec3(start.x(), start.y(), start.z());
		if (dragging == YAW) {
			Vec3 p = onGround(ray, o);
			if (p != null) {
				double yaw = startYaw + wrap(yawTowards(o, p) - grab);
				previewYaw = EditorState.snap == EditorState.Snap.OFF ? yaw : Math.round(yaw / 15) * 15.0;
			}
			return;
		}
		double t = along(ray, o, direction(dragging));
		if (Double.isNaN(t)) {
			return;
		}
		double moved = t - grab;
		previewPos = switch (dragging) {
			case X -> new io.github.firestormfmd.scenescripter.core.math.Vec3(snap(start.x() + moved, false), start.y(), start.z());
			case Y -> new io.github.firestormfmd.scenescripter.core.math.Vec3(start.x(), snap(start.y() + moved, true), start.z());
			default -> new io.github.firestormfmd.scenescripter.core.math.Vec3(start.x(), start.y(), snap(start.z() + moved, false));
		};
	}

	/** Ends the drag and clears the preview. */
	public static void cancel() {
		dragging = NONE;
		previewPos = null;
		previewYaw = null;
	}

	private static double snap(double v, boolean vertical) {
		return switch (EditorState.snap) {
			case OFF -> v;
			case HALF -> Math.round(v * 2) / 2.0;
			case BLOCK -> vertical ? Math.round(v) : Math.floor(v) + 0.5;
		};
	}

	private static double wrap(double degrees) {
		double d = degrees % 360;
		return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
	}

	/** Minecraft yaw, in degrees, looking from {@code o} towards {@code p}. */
	private static double yawTowards(Vec3 o, Vec3 p) {
		return Math.toDegrees(Math.atan2(-(p.x - o.x), p.z - o.z));
	}

	/** Where the ray meets the flat plane the ring lies on, or null when it runs parallel or away. */
	private static @Nullable Vec3 onGround(Picking.Ray ray, Vec3 o) {
		double planeY = o.y + LIFT;
		if (Math.abs(ray.direction().y) < 1.0e-4) {
			return null;
		}
		double s = (planeY - ray.from().y) / ray.direction().y;
		return s <= 0 ? null : ray.at(s);
	}

	/** How far along the axis line through {@code o} the ray passes closest, or NaN when it looks along it. */
	private static double along(Picking.Ray ray, Vec3 o, Vec3 axis) {
		Vec3 w = o.subtract(ray.from());
		double b = axis.dot(ray.direction());
		double denom = 1 - b * b;
		if (denom < 1.0e-6) {
			return Double.NaN;
		}
		double d = axis.dot(w);
		double e = ray.direction().dot(w);
		return (b * e - d) / denom;
	}

	/** Shortest distance between a ray and a line segment. */
	private static double rayToSegment(Picking.Ray ray, Vec3 a, Vec3 b) {
		Vec3 axis = b.subtract(a);
		double length = axis.length();
		Vec3 dir = axis.scale(1 / length);
		double t = along(ray, a, dir);
		t = Double.isNaN(t) ? 0 : Math.clamp(t, 0, length);
		Vec3 p = a.add(dir.scale(t));
		double s = Math.max(0, p.subtract(ray.from()).dot(ray.direction()));
		return ray.at(s).distanceTo(p);
	}
}
