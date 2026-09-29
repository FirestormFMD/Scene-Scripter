package io.github.firestormfmd.scenescripter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lets a falling-block actor show any block; vanilla only sets it when a real block starts to fall. */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockAccessor {
	@Accessor("blockState")
	void scenescripter$setBlockState(BlockState state);
}
