package io.github.firestormfmd.scenescripter.core.path;

import java.util.ArrayList;
import java.util.List;

import io.github.firestormfmd.scenescripter.core.math.BezierSpline;
import io.github.firestormfmd.scenescripter.core.math.CubicBezier;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;

/**
 * The curve of a motion path, looked up by distance travelled. Ground paths measure distance in the X/Z plane,
 * since walking speed is horizontal; their heights come from ground snapping instead of the curve.
 */
public final class PathGeometry {
	private final BezierSpline spline;
	private final Vec3 singlePoint;
	private final double lateralOffset;

	private PathGeometry(BezierSpline spline, Vec3 singlePoint, double lateralOffset) {
		this.spline = spline;
		this.singlePoint = singlePoint;
		this.lateralOffset = lateralOffset;
	}

	/**
	 * @param lateralOffset sideways offset in blocks, positive to the right of the direction of travel
	 */
	public static PathGeometry of(MotionPath path, double lateralOffset) {
		List<PathPoint> points = path.points();
		if (points.isEmpty()) {
			throw new IllegalArgumentException("Path " + path.id() + " has no points");
		}
		if (points.size() == 1) {
			return new PathGeometry(null, points.getFirst().pos(), lateralOffset);
		}

		List<Vec3> positions = points.stream().map(PathPoint::pos).toList();
		List<CubicBezier> segments = new ArrayList<>();
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3[] auto = BezierSpline.catmullRomControls(positions, i, 0.5);
			PathPoint a = points.get(i);
			PathPoint b = points.get(i + 1);
			Vec3 c1 = a.handleOut() != null ? a.pos().add(a.handleOut()) : auto[0];
			Vec3 c2 = b.handleIn() != null ? b.pos().add(b.handleIn()) : auto[1];
			segments.add(new CubicBezier(a.pos(), c1, c2, b.pos()));
		}
		return new PathGeometry(new BezierSpline(segments, path.kind() == PathKind.GROUND), null, lateralOffset);
	}

	public double length() {
		return spline == null ? 0 : spline.length();
	}

	/** Point on the curve (including the lateral offset) at the given distance. */
	public Vec3 pointAt(double distance) {
		if (spline == null) {
			return singlePoint;
		}
		Vec3 p = spline.pointAtDistance(distance);
		if (lateralOffset == 0) {
			return p;
		}
		Vec3 t = spline.tangentAtDistance(distance);
		// Right of the direction of travel in Minecraft coordinates: facing +Z (south), right is -X (west).
		Vec3 right = new Vec3(-t.z(), 0, t.x()).normalize();
		return p.add(right.scale(lateralOffset));
	}

	/** Unit direction of travel at the given distance, or {@link Vec3#ZERO} for a single-point path. */
	public Vec3 directionAt(double distance) {
		return spline == null ? Vec3.ZERO : spline.tangentAtDistance(distance);
	}
}
