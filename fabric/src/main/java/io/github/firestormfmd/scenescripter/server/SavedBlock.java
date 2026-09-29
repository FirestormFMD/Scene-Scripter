package io.github.firestormfmd.scenescripter.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * A block as the journal stores it: its state plus any block entity data, such as a chest's contents.
 */
public record SavedBlock(BlockState state, @Nullable CompoundTag blockEntity) {
}
