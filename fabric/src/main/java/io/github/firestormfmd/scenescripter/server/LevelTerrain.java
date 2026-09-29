package io.github.firestormfmd.scenescripter.server;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.firestormfmd.scenescripter.core.path.TerrainView;
import io.github.firestormfmd.scenescripter.core.scene.GroundFilter;

/**
 * Terrain for ground snapping, read from a level and filtered by the scene's ground filter. Unloaded chunks count
 * as empty so planning never loads or generates chunks.
 */
public final class LevelTerrain implements TerrainView {
	private final Level level;
	private final Set<Block> includeBlocks = new HashSet<>();
	private final Set<TagKey<Block>> includeTags = new HashSet<>();
	private final Set<Block> excludeBlocks = new HashSet<>();
	private final Set<TagKey<Block>> excludeTags = new HashSet<>();

	public LevelTerrain(Level level, GroundFilter filter) {
		this.level = level;
		compile(filter.include(), includeBlocks, includeTags);
		compile(filter.exclude(), excludeBlocks, excludeTags);
	}

	private static void compile(Iterable<String> entries, Set<Block> blocks, Set<TagKey<Block>> tags) {
		for (String entry : entries) {
			if (entry.startsWith("#")) {
				Identifier id = Identifier.tryParse(entry.substring(1));
				if (id != null) {
					tags.add(TagKey.create(Registries.BLOCK, id));
				}
			} else {
				Identifier id = Identifier.tryParse(entry);
				if (id != null) {
					BuiltInRegistries.BLOCK.get(id).ifPresent(ref -> blocks.add(ref.value()));
				}
			}
		}
	}

	private static boolean matches(BlockState state, Set<Block> blocks, Set<TagKey<Block>> tags) {
		if (blocks.contains(state.getBlock())) {
			return true;
		}
		for (TagKey<Block> tag : tags) {
			if (state.is(tag)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public double groundTop(int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (!level.isLoaded(pos)) {
			return Double.NaN;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || matches(state, excludeBlocks, excludeTags)) {
			return Double.NaN;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return matches(state, includeBlocks, includeTags) ? 1.0 : Double.NaN;
		}
		return shape.max(Direction.Axis.Y);
	}

	@Override
	public boolean obstructs(int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
	}

	@Override
	public double fluidTop(int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (!level.isLoaded(pos)) {
			return Double.NaN;
		}
		FluidState fluid = level.getBlockState(pos).getFluidState();
		return fluid.isEmpty() ? Double.NaN : fluid.getHeight(level, pos);
	}
}
