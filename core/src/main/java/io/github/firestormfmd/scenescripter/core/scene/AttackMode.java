package io.github.firestormfmd.scenescripter.core.scene;

public enum AttackMode {
	/** The attack lands only if it would hit in vanilla (reach, line of sight, target in front). */
	AUTO("auto"),
	/** Only the swing plays; hurt and death are animated by hand. */
	ANIMATION_ONLY("animation_only"),
	/** The attack always lands, ignoring reach. */
	ALWAYS_HIT("always_hit");

	private final String id;

	AttackMode(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static AttackMode byId(String id) {
		for (AttackMode m : values()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		throw new IllegalArgumentException("Unknown attack mode: " + id);
	}
}
