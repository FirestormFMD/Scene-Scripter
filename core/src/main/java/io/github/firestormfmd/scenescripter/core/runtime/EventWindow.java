package io.github.firestormfmd.scenescripter.core.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Finds the events the playhead passes.
 */
public final class EventWindow {
	private EventWindow() {
	}

	public record Fired(SceneObject owner, SceneEvent event) {
	}

	/** Events with {@code fromExclusive < tick <= toInclusive}, in tick order, then outliner order. */
	public static List<Fired> between(Scene scene, int fromExclusive, int toInclusive) {
		List<Fired> out = new ArrayList<>();
		List<SceneObject> owners = new ArrayList<>(scene.objects());
		owners.add(scene.tracks());
		for (SceneObject o : owners) {
			for (SceneEvent e : o.events()) {
				if (e.tick() > fromExclusive && e.tick() <= toInclusive) {
					out.add(new Fired(o, e));
				}
			}
		}
		out.sort(Comparator.comparingInt(f -> f.event().tick()));
		return out;
	}
}
