package io.github.firestormfmd.scenescripter.core.scene;

/**
 * How ground snapping treats water and lava.
 */
public enum FluidMode {
	/** The fluid surface counts as ground; objects swim across it. */
	SWIM("swim"),
	/** Fluids are ignored; objects walk along the bottom. */
	WALK_BOTTOM("walk_bottom"),
	/** Fluids block the path and are flagged as a problem. */
	BLOCKED("blocked");

	private final String id;

	FluidMode(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static FluidMode byId(String id) {
		for (FluidMode m : values()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		throw new IllegalArgumentException("Unknown fluid mode: " + id);
	}
}
