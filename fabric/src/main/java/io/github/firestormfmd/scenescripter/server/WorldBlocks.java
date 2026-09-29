package io.github.firestormfmd.scenescripter.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.firestormfmd.scenescripter.core.journal.BlockTarget;

/**
 * Lets the block journal read and write a level. Writes skip neighbour updates, drops and on-place effects, so a
 * scene's block changes never set off falling sand, flowing water or item drops.
 */
public final class WorldBlocks implements BlockTarget<SavedBlock> {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
			| Block.UPDATE_SKIP_ON_PLACE;

	private final ServerLevel level;

	public WorldBlocks(ServerLevel level) {
		this.level = level;
	}

	private static BlockPos mc(io.github.firestormfmd.scenescripter.core.math.BlockPos p) {
		return new BlockPos(p.x(), p.y(), p.z());
	}

	@Override
	public SavedBlock get(io.github.firestormfmd.scenescripter.core.math.BlockPos pos) {
		BlockPos p = mc(pos);
		BlockState state = level.getBlockState(p);
		BlockEntity be = level.getBlockEntity(p);
		CompoundTag tag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
		return new SavedBlock(state, tag);
	}

	@Override
	public void set(io.github.firestormfmd.scenescripter.core.math.BlockPos pos, SavedBlock block) {
		BlockPos p = mc(pos);
		level.setBlock(p, block.state(), FLAGS);
		if (block.blockEntity() != null) {
			BlockEntity be = BlockEntity.loadStatic(p, block.state(), block.blockEntity(), level.registryAccess());
			if (be != null) {
				level.setBlockEntity(be);
			}
		}
	}
}
