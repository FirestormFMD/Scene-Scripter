package io.github.firestormfmd.scenescripter.core.journal;

import java.util.List;

/**
 * Keeps the list of applied change sets on disk so a crash mid-scene can be repaired on the next load.
 */
@FunctionalInterface
public interface JournalStore<S> {
	JournalStore<?> NONE = applied -> {
	};

	@SuppressWarnings("unchecked")
	static <S> JournalStore<S> none() {
		return (JournalStore<S>) NONE;
	}

	/**
	 * Saves the applied change sets, oldest first. Called before any block is changed when applying, and after
	 * the blocks are restored when reverting, so the saved list always covers every change in the world.
	 */
	void write(List<AppliedChangeSet<S>> applied);
}
