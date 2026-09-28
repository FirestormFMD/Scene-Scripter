package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Objects;

/**
 * Scene-wide settings.
 *
 * @param rules scene-level interaction rules; every field should be set
 * @param trackingRange how far away, in blocks, actors are still sent to clients and recorders
 */
public record SceneSettings(GroundFilter groundFilter, InteractionRules rules, int trackingRange) {
	public static final SceneSettings DEFAULTS =
			new SceneSettings(GroundFilter.NATURAL_GROUND, InteractionRules.DEFAULTS, 160);

	public SceneSettings {
		Objects.requireNonNull(groundFilter, "groundFilter");
		Objects.requireNonNull(rules, "rules");
		if (trackingRange <= 0) {
			throw new IllegalArgumentException("Tracking range must be positive");
		}
	}

	public SceneSettings withGroundFilter(GroundFilter filter) {
		return new SceneSettings(filter, rules, trackingRange);
	}

	public SceneSettings withRules(InteractionRules newRules) {
		return new SceneSettings(groundFilter, newRules, trackingRange);
	}
}
