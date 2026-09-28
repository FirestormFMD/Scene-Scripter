package io.github.firestormfmd.scenescripter.core.math;

/**
 * One cubic Bézier segment from {@code p0} to {@code p3} with control points {@code p1} and {@code p2}.
 */
public record CubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
	public Vec3 point(double t) {
		double u = 1 - t;
		double b0 = u * u * u;
		double b1 = 3 * u * u * t;
		double b2 = 3 * u * t * t;
		double b3 = t * t * t;
		return new Vec3(
				b0 * p0.x() + b1 * p1.x() + b2 * p2.x() + b3 * p3.x(),
				b0 * p0.y() + b1 * p1.y() + b2 * p2.y() + b3 * p3.y(),
				b0 * p0.z() + b1 * p1.z() + b2 * p2.z() + b3 * p3.z());
	}

	/** First derivative with respect to {@code t}. */
	public Vec3 derivative(double t) {
		double u = 1 - t;
		Vec3 a = p1.subtract(p0).scale(3 * u * u);
		Vec3 b = p2.subtract(p1).scale(6 * u * t);
		Vec3 c = p3.subtract(p2).scale(3 * t * t);
		return a.add(b).add(c);
	}

	/**
	 * Evaluates a one-dimensional cubic Bézier with the given control values.
	 */
	public static double value(double v0, double v1, double v2, double v3, double t) {
		double u = 1 - t;
		return u * u * u * v0 + 3 * u * u * t * v1 + 3 * u * t * t * v2 + t * t * t * v3;
	}
}
