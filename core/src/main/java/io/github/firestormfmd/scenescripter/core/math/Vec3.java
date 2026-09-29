package io.github.firestormfmd.scenescripter.core.math;

/**
 * Immutable 3D vector in world space (blocks).
 */
public record Vec3(double x, double y, double z) {
	public static final Vec3 ZERO = new Vec3(0, 0, 0);

	public Vec3 add(Vec3 o) {
		return new Vec3(x + o.x, y + o.y, z + o.z);
	}

	public Vec3 add(double dx, double dy, double dz) {
		return new Vec3(x + dx, y + dy, z + dz);
	}

	public Vec3 subtract(Vec3 o) {
		return new Vec3(x - o.x, y - o.y, z - o.z);
	}

	public Vec3 scale(double f) {
		return new Vec3(x * f, y * f, z * f);
	}

	public double dot(Vec3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	public double lengthSquared() {
		return x * x + y * y + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSquared());
	}

	/** Length ignoring the vertical component. */
	public double horizontalLength() {
		return Math.sqrt(x * x + z * z);
	}

	public double distanceTo(Vec3 o) {
		return subtract(o).length();
	}

	/** Returns this vector scaled to length 1, or {@link #ZERO} if it has no length. */
	public Vec3 normalize() {
		double len = length();
		return len < 1.0e-9 ? ZERO : scale(1.0 / len);
	}

	public Vec3 withY(double newY) {
		return new Vec3(x, newY, z);
	}

	public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
		return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	/**
	 * Minecraft yaw in degrees for travelling along this direction: 0 faces +Z (south), 90 faces -X (west).
	 */
	public float yaw() {
		return (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0);
	}
}
