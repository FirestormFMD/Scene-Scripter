package io.github.firestormfmd.scenescripter.core.scene;

public enum CritMode {
	/** Critical hit when the vanilla conditions are met, such as falling and not sprinting. */
	AUTO("auto"),
	ALWAYS("always"),
	NEVER("never");

	private final String id;

	CritMode(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static CritMode byId(String id) {
		for (CritMode m : values()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		throw new IllegalArgumentException("Unknown crit mode: " + id);
	}
}
