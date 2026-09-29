package io.github.firestormfmd.scenescripter.core.scene;

/**
 * Walking speed at a point along a path. Speed changes linearly between keys.
 *
 * @param at position along the path as a fraction of its length (0 to 1)
 * @param speed blocks per second, always positive; use a wait marker to stop
 */
public record SpeedKey(double at, double speed) {
	public SpeedKey {
		if (at < 0 || at > 1) {
			throw new IllegalArgumentException("Speed key position must be between 0 and 1: " + at);
		}
		if (!(speed > 0)) {
			throw new IllegalArgumentException("Speed must be positive: " + speed);
		}
	}
}
