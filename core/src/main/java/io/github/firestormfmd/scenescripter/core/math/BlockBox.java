package io.github.firestormfmd.scenescripter.core.math;

/**
 * Inclusive box of blocks, used for scene bounds.
 */
public record BlockBox(BlockPos min, BlockPos max) {
	public BlockBox {
		BlockPos lo = new BlockPos(Math.min(min.x(), max.x()), Math.min(min.y(), max.y()), Math.min(min.z(), max.z()));
		BlockPos hi = new BlockPos(Math.max(min.x(), max.x()), Math.max(min.y(), max.y()), Math.max(min.z(), max.z()));
		min = lo;
		max = hi;
	}

	public boolean contains(BlockPos p) {
		return p.x() >= min.x() && p.x() <= max.x()
				&& p.y() >= min.y() && p.y() <= max.y()
				&& p.z() >= min.z() && p.z() <= max.z();
	}
}
