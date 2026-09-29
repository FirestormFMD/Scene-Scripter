package io.github.firestormfmd.scenescripter.core.scene;

/**
 * How an object moves along a path. Each gait sets the matching vanilla flags or pose on the actor.
 */
public enum Gait {
	WALK("walk", 4.317),
	SPRINT("sprint", 5.612),
	SNEAK("sneak", 1.295),
	SWIM("swim", 1.97);

	private final String id;
	private final double playerSpeed;

	Gait(String id, double playerSpeed) {
		this.id = id;
		this.playerSpeed = playerSpeed;
	}

	public String id() {
		return id;
	}

	/** Vanilla player speed for this gait in blocks per second, used as a preset. */
	public double playerSpeed() {
		return playerSpeed;
	}

	public static Gait byId(String id) {
		for (Gait g : values()) {
			if (g.id.equals(id)) {
				return g;
			}
		}
		throw new IllegalArgumentException("Unknown gait: " + id);
	}
}
