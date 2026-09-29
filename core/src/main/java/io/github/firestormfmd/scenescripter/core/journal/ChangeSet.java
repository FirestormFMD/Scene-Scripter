package io.github.firestormfmd.scenescripter.core.journal;

import java.util.List;
import java.util.Objects;

/**
 * The block changes one event makes at one tick, such as the crater of an explosion.
 *
 * @param id the event's ID
 */
public record ChangeSet<S>(String id, int tick, List<BlockChange<S>> changes) {
	public ChangeSet {
		Objects.requireNonNull(id, "id");
		changes = List.copyOf(changes);
	}
}
