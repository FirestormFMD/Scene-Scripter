package io.github.firestormfmd.scenescripter.core.capture;

import java.util.List;
import java.util.Objects;

import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;

/**
 * One recorded performance of an object: a sample per tick plus the events the performer caused (swings, attacks,
 * block interactions). Takes are kept after they are applied so another one can be picked later.
 *
 * @param objectId the object the take was recorded for
 * @param samples one per tick, in tick order
 */
public record Take(String id, String objectId, String name, long recordedAt, List<CaptureSample> samples,
		List<SceneEvent> events) {
	public Take {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(objectId, "objectId");
		samples = List.copyOf(samples);
		events = List.copyOf(events);
		if (samples.isEmpty()) {
			throw new IllegalArgumentException("A take needs at least one sample");
		}
	}

	public int startTick() {
		return samples.getFirst().tick();
	}

	public int endTick() {
		return samples.getLast().tick();
	}
}
