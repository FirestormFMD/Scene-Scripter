package io.github.firestormfmd.scenescripter.core.scene;

public enum PathKind {
	/** Drawn in plan view and snapped onto the ground; objects walk, step, jump and fall along it. */
	GROUND("ground"),
	/** A free 3D curve with no snapping, for flying objects. */
	AIR("air");

	private final String id;

	PathKind(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static PathKind byId(String id) {
		for (PathKind k : values()) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		throw new IllegalArgumentException("Unknown path kind: " + id);
	}
}
