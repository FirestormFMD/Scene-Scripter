package io.github.firestormfmd.scenescripter.core.solve;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * Vanilla projectile flight: each tick the projectile moves by its velocity, then drag slows it and gravity pulls
 * it down. Also aims shots, finding the pitch that lands a projectile on a point.
 */
public final class Ballistics {
	/**
	 * How one kind of projectile flies.
	 *
	 * @param speed launch speed in blocks per tick at full power
	 * @param gravity downward acceleration per tick
	 * @param drag velocity kept each tick in air
	 * @param sticks stays where it hits a block (arrows, tridents) instead of breaking
	 * @param baseDamage damage per unit of speed for arrows; flat damage for everything else
	 * @param scalesWithSpeed damage is {@code ceil(speed * baseDamage)} like arrows
	 * @param explodes explodes where it lands (fireballs)
	 */
	public record Kind(String entityType, double speed, double gravity, double drag, boolean sticks, double baseDamage,
			boolean scalesWithSpeed, boolean explodes) {
	}

	public static final Kind ARROW = new Kind("minecraft:arrow", 3.0, 0.05, 0.99, true, 2.0, true, false);

	private Ballistics() {
	}

	public static Kind kind(String entityType) {
		return switch (entityType) {
			case "minecraft:arrow", "minecraft:spectral_arrow" -> new Kind(entityType, 3.0, 0.05, 0.99, true, 2.0, true, false);
			case "minecraft:trident" -> new Kind(entityType, 2.5, 0.05, 0.99, true, 8.0, false, false);
			case "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl" ->
					new Kind(entityType, 1.5, 0.03, 0.99, false, 0.0, false, false);
			case "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:experience_bottle" ->
					new Kind(entityType, 0.5, 0.05, 0.99, false, 0.0, false, false);
			case "minecraft:small_fireball" -> new Kind(entityType, 1.0, 0.0, 1.0, false, 5.0, false, false);
			case "minecraft:fireball" -> new Kind(entityType, 1.0, 0.0, 1.0, false, 6.0, false, true);
			case "minecraft:wind_charge" -> new Kind(entityType, 1.5, 0.0, 1.0, false, 1.0, false, false);
			default -> new Kind(entityType, 1.5, 0.03, 0.99, false, 0.0, false, false);
		};
	}

	/** Velocity for a shot in the Minecraft facing convention (yaw 0 is south, negative pitch is up). */
	public static Vec3 velocity(float yaw, float pitch, double speed) {
		double yr = Math.toRadians(yaw);
		double pr = Math.toRadians(pitch);
		return new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)).scale(speed);
	}

	/** One tick of flight: returns {position, velocity} after the move. */
	public static Vec3[] step(Kind kind, Vec3 pos, Vec3 vel) {
		Vec3 next = pos.add(vel);
		Vec3 v = vel.scale(kind.drag()).add(0, -kind.gravity(), 0);
		return new Vec3[] {next, v};
	}

	/** Arrow damage at a given impact speed ({@code AbstractArrow.onHitEntity}), before any critical bonus. */
	public static double damage(Kind kind, Vec3 velocity) {
		if (kind.scalesWithSpeed()) {
			return Math.ceil(Math.clamp(velocity.length() * kind.baseDamage(), 0.0, Integer.MAX_VALUE));
		}
		return kind.baseDamage();
	}

	/**
	 * Yaw and pitch that land a projectile launched at {@code speed} from {@code from} on {@code to}, taking the
	 * low arc. If the target is out of range, aims for the longest shot.
	 *
	 * @return {yaw, pitch, reachable ? 1 : 0}
	 */
	public static float[] aim(Kind kind, Vec3 from, Vec3 to, double speed) {
		Vec3 d = to.subtract(from);
		float yaw = d.horizontalLength() < 1.0e-9 ? 0 : d.yaw();
		double distance = d.horizontalLength();
		if (kind.gravity() == 0 || distance < 1.0e-9) {
			float pitch = (float) -Math.toDegrees(Math.atan2(d.y(), Math.max(distance, 1.0e-9)));
			return new float[] {yaw, pitch, 1};
		}
		// Height at the target's distance rises with the launch angle up to the longest shot, so scan upward from
		// straight at the target and refine the first crossing.
		float prev = Math.min((float) -Math.toDegrees(Math.atan2(d.y(), distance)), 89);
		if (heightAt(kind, speed, prev, distance) >= d.y()) {
			return new float[] {yaw, prev, 1};
		}
		float best = prev;
		double bestHeight = Double.NEGATIVE_INFINITY;
		for (float p = prev - 1; p >= -89; p -= 1) {
			double h = heightAt(kind, speed, p, distance);
			if (h >= d.y()) {
				float lo = p;
				float hi = prev;
				for (int i = 0; i < 30; i++) {
					float mid = (lo + hi) / 2;
					if (heightAt(kind, speed, mid, distance) >= d.y()) {
						lo = mid;
					} else {
						hi = mid;
					}
				}
				return new float[] {yaw, lo, 1};
			}
			if (h > bestHeight) {
				best = p;
				bestHeight = h;
			}
			prev = p;
		}
		return new float[] {yaw, best, 0};
	}

	/** Height relative to the launch point when the shot has flown {@code distance} horizontally. */
	static double heightAt(Kind kind, double speed, float pitch, double distance) {
		Vec3 pos = Vec3.ZERO;
		Vec3 vel = velocity(0, pitch, speed);
		for (int i = 0; i < 400; i++) {
			Vec3[] s = step(kind, pos, vel);
			if (s[0].z() >= distance) {
				double f = (distance - pos.z()) / (s[0].z() - pos.z());
				return pos.y() + (s[0].y() - pos.y()) * f;
			}
			if (vel.z() <= 1.0e-6 && s[1].z() <= 1.0e-6) {
				break;
			}
			pos = s[0];
			vel = s[1];
		}
		return Double.NEGATIVE_INFINITY;
	}

	/** Where a segment first enters an axis-aligned box, as a fraction of the segment, or -1 if it misses. */
	public static double segmentHitsBox(Vec3 from, Vec3 to, Vec3 min, Vec3 max) {
		double t0 = 0;
		double t1 = 1;
		double[] f = {from.x(), from.y(), from.z()};
		double[] d = {to.x() - from.x(), to.y() - from.y(), to.z() - from.z()};
		double[] lo = {min.x(), min.y(), min.z()};
		double[] hi = {max.x(), max.y(), max.z()};
		for (int i = 0; i < 3; i++) {
			if (Math.abs(d[i]) < 1.0e-12) {
				if (f[i] < lo[i] || f[i] > hi[i]) {
					return -1;
				}
				continue;
			}
			double a = (lo[i] - f[i]) / d[i];
			double b = (hi[i] - f[i]) / d[i];
			if (a > b) {
				double t = a;
				a = b;
				b = t;
			}
			t0 = Math.max(t0, a);
			t1 = Math.min(t1, b);
			if (t0 > t1) {
				return -1;
			}
		}
		return t0;
	}
}
