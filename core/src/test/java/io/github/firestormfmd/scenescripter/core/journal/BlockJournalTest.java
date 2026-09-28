package io.github.firestormfmd.scenescripter.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;

class BlockJournalTest {
	/** World of named blocks; anything unset is "stone". */
	private static final class World implements BlockTarget<String> {
		final Map<BlockPos, String> blocks = new HashMap<>();

		@Override
		public String get(BlockPos pos) {
			return blocks.getOrDefault(pos, "stone");
		}

		@Override
		public void set(BlockPos pos, String state) {
			if (state.equals("stone")) {
				blocks.remove(pos);
			} else {
				blocks.put(pos, state);
			}
		}
	}

	private static final BlockPos A = new BlockPos(0, 64, 0);
	private static final BlockPos B = new BlockPos(1, 64, 0);

	private static ChangeSet<String> set(String id, int tick, Object... posAndState) {
		List<BlockChange<String>> changes = new ArrayList<>();
		for (int i = 0; i < posAndState.length; i += 2) {
			changes.add(new BlockChange<>((BlockPos) posAndState[i], (String) posAndState[i + 1]));
		}
		return new ChangeSet<>(id, tick, changes);
	}

	@Test
	void seekAppliesAndRevertsByTick() {
		World world = new World();
		world.set(A, "gold");
		BlockJournal<String> journal = new BlockJournal<>(JournalStore.none());
		journal.setChangeSets(List.of(set("boom", 100, A, "air", B, "air")), world);

		journal.seek(99, world);
		assertEquals("gold", world.get(A));

		journal.seek(100, world);
		assertEquals("air", world.get(A));
		assertEquals("air", world.get(B));

		journal.seek(0, world);
		assertEquals("gold", world.get(A));
		assertEquals("stone", world.get(B));
		assertTrue(journal.atBaseState());
	}

	@Test
	void overlappingChangesRestoreInOrder() {
		World world = new World();
		BlockJournal<String> journal = new BlockJournal<>(JournalStore.none());
		journal.setChangeSets(List.of(
				set("place", 150, A, "tnt"),
				set("boom", 100, A, "air")), world);

		journal.seek(200, world);
		assertEquals("tnt", world.get(A));
		journal.seek(120, world);
		assertEquals("air", world.get(A));
		journal.seek(0, world);
		assertEquals("stone", world.get(A));
	}

	@Test
	void restoresWhatWasReallyThereWhenApplied() {
		World world = new World();
		BlockJournal<String> journal = new BlockJournal<>(JournalStore.none());
		journal.setChangeSets(List.of(set("boom", 10, A, "air")), world);
		world.set(A, "diamond"); // the world changed after the explosion was planned
		journal.seek(20, world);
		journal.revertAll(world);
		assertEquals("diamond", world.get(A));
	}

	@Test
	void storeIsWrittenBeforeTheWorldChanges() {
		World world = new World();
		List<String> seenBeforeChange = new ArrayList<>();
		BlockJournal<String> journal = new BlockJournal<>(applied -> {
			if (!applied.isEmpty()) {
				seenBeforeChange.add(world.get(A));
			}
		});
		journal.setChangeSets(List.of(set("boom", 10, A, "air")), world);
		journal.seek(10, world);
		assertEquals(List.of("stone"), seenBeforeChange);
	}

	@Test
	void crashRecoveryRevertsSavedSets() {
		World world = new World();
		List<List<AppliedChangeSet<String>>> saved = new ArrayList<>();
		BlockJournal<String> journal = new BlockJournal<>(saved::add);
		journal.setChangeSets(List.of(set("a", 10, A, "air"), set("b", 20, A, "tnt", B, "air")), world);
		journal.seek(30, world);

		// Simulate a crash: the journal object is lost, only the last saved list survives.
		BlockJournal.restore(saved.getLast(), world);
		assertEquals("stone", world.get(A));
		assertEquals("stone", world.get(B));
	}

	@Test
	void rebakingOnlyRevertsFromTheFirstChangedSet() {
		World world = new World();
		List<String> reverted = new ArrayList<>();
		BlockTarget<String> tracking = new BlockTarget<>() {
			@Override
			public String get(BlockPos pos) {
				return world.get(pos);
			}

			@Override
			public void set(BlockPos pos, String state) {
				if (pos.equals(A) && state.equals("stone")) {
					reverted.add("a");
				}
				world.set(pos, state);
			}
		};
		BlockJournal<String> journal = new BlockJournal<>(JournalStore.none());
		journal.setChangeSets(List.of(set("a", 10, A, "air"), set("b", 20, B, "air")), tracking);
		journal.seek(30, tracking);

		journal.setChangeSets(List.of(set("a", 10, A, "air"), set("b", 20, B, "glass")), tracking);
		assertTrue(reverted.isEmpty(), "unchanged set 'a' should stay applied");
		assertEquals("glass", world.get(B));
		assertEquals("air", world.get(A));
	}
}
