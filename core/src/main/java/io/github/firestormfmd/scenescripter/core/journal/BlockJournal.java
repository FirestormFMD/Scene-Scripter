package io.github.firestormfmd.scenescripter.core.journal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;

/**
 * Applies and reverts a scene's block changes as the playhead moves, so every change can be undone.
 *
 * <p>Change sets are ordered by tick. At any playhead position exactly the sets at or before it are applied, and
 * they are undone in reverse order, so overlapping changes (a block blown up, then rebuilt) restore correctly.
 * Each set records the states it replaced when it is applied, not when it was planned, so the world is restored
 * to what was really there.
 */
public final class BlockJournal<S> {
	private final JournalStore<S> store;
	private final List<ChangeSet<S>> sets = new ArrayList<>();
	/** Applied sets, oldest first. Always the first {@code applied.size()} entries of {@link #sets}. */
	private final List<AppliedChangeSet<S>> applied = new ArrayList<>();
	private int playhead = Integer.MIN_VALUE;

	public BlockJournal(JournalStore<S> store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	/** Change sets currently applied to the world, oldest first. */
	public List<AppliedChangeSet<S>> applied() {
		return Collections.unmodifiableList(applied);
	}

	/** True when no scene change is in the world, so manual building is safe. */
	public boolean atBaseState() {
		return applied.isEmpty();
	}

	public int playhead() {
		return playhead;
	}

	/**
	 * Replaces the planned change sets, for example after the solver re-bakes an explosion. Sets that are applied
	 * and unchanged stay in the world; everything from the first difference on is reverted and re-applied.
	 */
	public void setChangeSets(List<ChangeSet<S>> newSets, BlockTarget<S> world) {
		List<ChangeSet<S>> sorted = new ArrayList<>(newSets);
		sorted.sort(Comparator.comparingInt(ChangeSet::tick));

		int keep = 0;
		while (keep < applied.size() && keep < sorted.size() && sets.get(keep).equals(sorted.get(keep))) {
			keep++;
		}
		while (applied.size() > keep) {
			revertLast(world);
		}
		sets.clear();
		sets.addAll(sorted);
		if (playhead != Integer.MIN_VALUE) {
			seek(playhead, world);
		}
	}

	/** Brings the world to the state at {@code tick}: sets at or before it applied, later ones reverted. */
	public void seek(int tick, BlockTarget<S> world) {
		playhead = tick;
		int target = 0;
		while (target < sets.size() && sets.get(target).tick() <= tick) {
			target++;
		}
		while (applied.size() > target) {
			revertLast(world);
		}
		while (applied.size() < target) {
			applyNext(world);
		}
	}

	/** Undoes every applied change, returning the world to its base state. */
	public void revertAll(BlockTarget<S> world) {
		while (!applied.isEmpty()) {
			revertLast(world);
		}
		playhead = Integer.MIN_VALUE;
	}

	/**
	 * Undoes change sets saved by a previous session that ended without reverting them, such as after a crash.
	 */
	public static <S> void restore(List<AppliedChangeSet<S>> saved, BlockTarget<S> world) {
		for (int i = saved.size() - 1; i >= 0; i--) {
			undo(saved.get(i), world);
		}
	}

	private void applyNext(BlockTarget<S> world) {
		ChangeSet<S> set = sets.get(applied.size());
		Map<BlockPos, BlockChange<S>> previous = new LinkedHashMap<>();
		for (BlockChange<S> change : set.changes()) {
			// If a set touches a block twice, the first original state is the one to restore.
			previous.computeIfAbsent(change.pos(), p -> new BlockChange<>(p, world.get(p)));
		}
		applied.add(new AppliedChangeSet<>(set.id(), set.tick(), new ArrayList<>(previous.values())));
		// Write-ahead: the store must know how to undo this set before the world changes.
		store.write(List.copyOf(applied));
		for (BlockChange<S> change : set.changes()) {
			world.set(change.pos(), change.state());
		}
	}

	private void revertLast(BlockTarget<S> world) {
		AppliedChangeSet<S> last = applied.removeLast();
		undo(last, world);
		store.write(List.copyOf(applied));
	}

	private static <S> void undo(AppliedChangeSet<S> set, BlockTarget<S> world) {
		List<BlockChange<S>> previous = set.previous();
		for (int i = previous.size() - 1; i >= 0; i--) {
			world.set(previous.get(i).pos(), previous.get(i).state());
		}
	}
}
