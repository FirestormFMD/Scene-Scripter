package io.github.firestormfmd.scenescripter.core.journal;

import java.util.List;
import java.util.Objects;

/**
 * A change set that has been applied, with the block states it replaced: everything needed to undo it, even after
 * a crash.
 */
public record AppliedChangeSet<S>(String id, int tick, List<BlockChange<S>> previous) {
	public AppliedChangeSet {
		Objects.requireNonNull(id, "id");
		previous = List.copyOf(previous);
	}
}
