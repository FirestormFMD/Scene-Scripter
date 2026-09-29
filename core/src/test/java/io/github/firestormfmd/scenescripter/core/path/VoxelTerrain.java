package io.github.firestormfmd.scenescripter.core.path;

import java.util.HashMap;
import java.util.Map;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;

/** Small in-memory world for path tests. Leaves are treated as excluded from ground, like the default filter. */
final class VoxelTerrain implements TerrainView {
	enum Block {
		STONE(1, true, Double.NaN),
		SLAB(0.5, true, Double.NaN),
		FENCE(1.5, true, Double.NaN),
		LEAVES(Double.NaN, true, Double.NaN),
		WATER(Double.NaN, false, 8.0 / 9);

		final double top;
		final boolean obstructs;
		final double fluid;

		Block(double top, boolean obstructs, double fluid) {
			this.top = top;
			this.obstructs = obstructs;
			this.fluid = fluid;
		}
	}

	private final Map<BlockPos, Block> blocks = new HashMap<>();

	VoxelTerrain fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					blocks.put(new BlockPos(x, y, z), block);
				}
			}
		}
		return this;
	}

	/** Stone floor whose top surface is at y = 64. */
	static VoxelTerrain floor() {
		return new VoxelTerrain().fill(-20, 63, -20, 60, 63, 20, Block.STONE);
	}

	private Block at(int x, int y, int z) {
		return blocks.get(new BlockPos(x, y, z));
	}

	@Override
	public double groundTop(int x, int y, int z) {
		Block b = at(x, y, z);
		return b == null ? Double.NaN : b.top;
	}

	@Override
	public boolean obstructs(int x, int y, int z) {
		Block b = at(x, y, z);
		return b != null && b.obstructs;
	}

	@Override
	public double fluidTop(int x, int y, int z) {
		Block b = at(x, y, z);
		return b == null ? Double.NaN : b.fluid;
	}
}
