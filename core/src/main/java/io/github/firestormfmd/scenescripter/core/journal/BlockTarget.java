package io.github.firestormfmd.scenescripter.core.journal;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;

/**
 * The world the journal changes. On the Minecraft side {@code S} holds a block state plus any block entity data.
 */
public interface BlockTarget<S> {
	S get(BlockPos pos);

	/** Sets a block without triggering neighbour updates, so scene changes don't set off falling sand or flowing water. */
	void set(BlockPos pos, S state);
}
