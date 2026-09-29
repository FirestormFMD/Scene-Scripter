package io.github.firestormfmd.scenescripter.core.solve;

import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * The solver's virtual copy of the world. It starts as the world with no scene changes applied, and the solver
 * changes it in tick order as it bakes explosions and block events, so a later explosion sees the crater of an
 * earlier one. Every change is recorded under the event that made it; the Minecraft side turns the records into
 * the block journal's change sets.
 */
public interface BlockWorld {
	/**
	 * Explosion resistance of the block or fluid at a position, whichever is higher, or a negative number when
	 * there is nothing there (air with no fluid).
	 */
	float explosionResistance(BlockPos pos);

	/** Whether the height is inside the world, where explosion rays stop. */
	boolean inBuildHeight(int y);

	boolean isTnt(BlockPos pos);

	/** Whether fire could be lit here: nothing in the space and a solid block below. */
	boolean canLightFire(BlockPos pos);

	/** Where the segment first hits a block's collision shape, if it does. */
	Optional<Vec3> clip(Vec3 from, Vec3 to);

	/** Replaces the block with air, without drops. */
	void destroy(BlockPos pos, String cause, int tick);

	void lightFire(BlockPos pos, String cause, int tick);

	/**
	 * Places a block.
	 *
	 * @param blockState block state in command syntax, such as {@code minecraft:oak_stairs[facing=east]}
	 * @return false if the state could not be read
	 */
	boolean place(BlockPos pos, String blockState, String cause, int tick);

	/**
	 * Uses a block the way a player's right click would where that only flips its state: doors (both halves),
	 * trapdoors, fence gates, levers and buttons.
	 *
	 * @return false if the block has nothing to flip
	 */
	boolean use(BlockPos pos, String cause, int tick);

	/** Back to the world with no scene changes, forgetting everything recorded. */
	void reset();

	/** A world of air, for solving without a level. */
	BlockWorld EMPTY = new BlockWorld() {
		@Override
		public float explosionResistance(BlockPos pos) {
			return -1;
		}

		@Override
		public boolean inBuildHeight(int y) {
			return true;
		}

		@Override
		public boolean isTnt(BlockPos pos) {
			return false;
		}

		@Override
		public boolean canLightFire(BlockPos pos) {
			return false;
		}

		@Override
		public Optional<Vec3> clip(Vec3 from, Vec3 to) {
			return Optional.empty();
		}

		@Override
		public void destroy(BlockPos pos, String cause, int tick) {
		}

		@Override
		public void lightFire(BlockPos pos, String cause, int tick) {
		}

		@Override
		public boolean place(BlockPos pos, String blockState, String cause, int tick) {
			return false;
		}

		@Override
		public boolean use(BlockPos pos, String cause, int tick) {
			return false;
		}

		@Override
		public void reset() {
		}
	};
}
