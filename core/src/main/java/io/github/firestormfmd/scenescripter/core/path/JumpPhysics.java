package io.github.firestormfmd.scenescripter.core.path;

/**
 * Vanilla vertical movement: each tick the entity moves by its vertical velocity, then gravity is subtracted and
 * drag applied. Using the same constants keeps jumps and falls identical to real mobs and players.
 */
public final class JumpPhysics {
	public static final double GRAVITY = 0.08;
	public static final double DRAG = 0.98;
	/** Default {@code jump_strength}; reaches about 1.25 blocks. */
	public static final double VANILLA_JUMP_VELOCITY = 0.42;
	/** Vertical velocity a grounded entity carries into its first tick after walking off an edge. */
	public static final double EDGE_FALL_VELOCITY = -GRAVITY * DRAG;

	private JumpPhysics() {
	}

	/** Velocity after one tick of gravity and drag. */
	public static double nextVelocity(double vy) {
		return (vy - GRAVITY) * DRAG;
	}

	/** Highest point reached above the takeoff height for a jump with the given takeoff velocity. */
	public static double apexHeight(double velocity) {
		double y = 0;
		double vy = velocity;
		while (vy > 0) {
			y += vy;
			vy = nextVelocity(vy);
		}
		return y;
	}

	/** Takeoff velocity whose jump peaks at the given height. */
	public static double velocityForHeight(double height) {
		if (!(height > 0)) {
			throw new IllegalArgumentException("Jump height must be positive: " + height);
		}
		double lo = 0;
		double hi = 1;
		while (apexHeight(hi) < height) {
			hi *= 2;
		}
		for (int i = 0; i < 60; i++) {
			double mid = (lo + hi) / 2;
			if (apexHeight(mid) < height) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return hi;
	}
}
