package io.github.firestormfmd.scenescripter.core.scene;

public enum TimingMode {
	/** Start tick and speed are set; the end tick follows from them. */
	SPEED("speed"),
	/** Start and end ticks are pinned; speed is scaled to fit. */
	FIT("fit");

	private final String id;

	TimingMode(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static TimingMode byId(String id) {
		for (TimingMode m : values()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		throw new IllegalArgumentException("Unknown timing mode: " + id);
	}
}
