package io.github.firestormfmd.scenescripter.core.math;

import java.util.ArrayList;
import java.util.List;

/**
 * A chain of cubic Bézier segments with an arc-length table, so positions can be looked up by distance travelled.
 */
public final class BezierSpline {
	private static final int SAMPLES_PER_SEGMENT = 64;

	private final List<CubicBezier> segments;
	private final boolean horizontal;
	/** Arc length at each sample, starting at 0. */
	private final double[] lengths;
	/** Global curve parameter (segment index + local t) at each sample. */
	private final double[] params;

	/**
	 * @param horizontal if true, arc length is measured in the X/Z plane only, which is what walking speed refers to
	 */
	public BezierSpline(List<CubicBezier> segments, boolean horizontal) {
		this.segments = List.copyOf(segments);
		this.horizontal = horizontal;

		int n = this.segments.size() * SAMPLES_PER_SEGMENT + 1;
		this.lengths = new double[n];
		this.params = new double[n];

		if (this.segments.isEmpty()) {
			return;
		}

		Vec3 prev = this.segments.getFirst().p0();
		int idx = 1;
		for (int s = 0; s < this.segments.size(); s++) {
			CubicBezier seg = this.segments.get(s);
			for (int i = 1; i <= SAMPLES_PER_SEGMENT; i++) {
				double t = (double) i / SAMPLES_PER_SEGMENT;
				Vec3 p = seg.point(t);
				Vec3 d = p.subtract(prev);
				lengths[idx] = lengths[idx - 1] + (horizontal ? d.horizontalLength() : d.length());
				params[idx] = s + t;
				prev = p;
				idx++;
			}
		}
	}

	/**
	 * Builds a centripetal Catmull-Rom spline (alpha 0.5) through the given points. Centripetal parameterisation
	 * avoids the loops and cusps uniform Catmull-Rom produces when points are unevenly spaced.
	 */
	public static BezierSpline catmullRom(List<Vec3> points, boolean horizontal) {
		List<CubicBezier> segs = new ArrayList<>();
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3[] c = catmullRomControls(points, i, 0.5);
			segs.add(new CubicBezier(points.get(i), c[0], c[1], points.get(i + 1)));
		}
		return new BezierSpline(segs, horizontal);
	}

	/**
	 * Inner Bézier control points of the Catmull-Rom segment from {@code points[i]} to {@code points[i + 1]}.
	 * Missing neighbours at the ends are mirrored.
	 *
	 * @param alpha 0 for uniform, 0.5 for centripetal, 1 for chordal
	 */
	public static Vec3[] catmullRomControls(List<Vec3> points, int i, double alpha) {
		Vec3 p1 = points.get(i);
		Vec3 p2 = points.get(i + 1);
		Vec3 p0 = i > 0 ? points.get(i - 1) : p1.scale(2).subtract(p2);
		Vec3 p3 = i + 2 < points.size() ? points.get(i + 2) : p2.scale(2).subtract(p1);

		double d1 = knot(p0, p1, alpha);
		double d2 = knot(p1, p2, alpha);
		double d3 = knot(p2, p3, alpha);

		// Barry-Goldman non-uniform Catmull-Rom expressed as Bézier control points.
		Vec3 b1 = p2.scale(d1 * d1)
				.subtract(p0.scale(d2 * d2))
				.add(p1.scale(2 * d1 * d1 + 3 * d1 * d2 + d2 * d2))
				.scale(1.0 / (3 * d1 * (d1 + d2)));
		Vec3 b2 = p1.scale(d3 * d3)
				.subtract(p3.scale(d2 * d2))
				.add(p2.scale(2 * d3 * d3 + 3 * d3 * d2 + d2 * d2))
				.scale(1.0 / (3 * d3 * (d3 + d2)));
		return new Vec3[] {b1, b2};
	}

	private static double knot(Vec3 a, Vec3 b, double alpha) {
		// Coincident points would divide by zero; treat them as a tiny gap instead.
		return Math.max(Math.pow(a.distanceTo(b), alpha), 1.0e-4);
	}

	public List<CubicBezier> segments() {
		return segments;
	}

	public boolean isHorizontal() {
		return horizontal;
	}

	public double length() {
		return lengths.length == 0 ? 0 : lengths[lengths.length - 1];
	}

	public Vec3 pointAtDistance(double distance) {
		if (segments.isEmpty()) {
			throw new IllegalStateException("Spline has no segments");
		}
		double p = paramAtDistance(distance);
		int seg = Math.min((int) p, segments.size() - 1);
		return segments.get(seg).point(p - seg);
	}

	/** Unit tangent at the given distance, or {@link Vec3#ZERO} where the curve has no direction. */
	public Vec3 tangentAtDistance(double distance) {
		if (segments.isEmpty()) {
			return Vec3.ZERO;
		}
		double p = paramAtDistance(distance);
		int seg = Math.min((int) p, segments.size() - 1);
		Vec3 d = segments.get(seg).derivative(p - seg);
		if (horizontal) {
			d = d.withY(0);
		}
		return d.normalize();
	}

	/** Global curve parameter (segment index + local t) at the given arc length, clamped to the curve. */
	public double paramAtDistance(double distance) {
		if (lengths.length <= 1) {
			return 0;
		}
		double total = length();
		if (distance <= 0) {
			return 0;
		}
		if (distance >= total) {
			return segments.size();
		}

		int lo = 0;
		int hi = lengths.length - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (lengths[mid] <= distance) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		double span = lengths[hi] - lengths[lo];
		double f = span < 1.0e-12 ? 0 : (distance - lengths[lo]) / span;
		return params[lo] + (params[hi] - params[lo]) * f;
	}
}
