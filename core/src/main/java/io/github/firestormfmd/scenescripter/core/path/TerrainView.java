package io.github.firestormfmd.scenescripter.core.path;

/**
 * Read-only view of the blocks paths are snapped onto. The Minecraft side implements this over a level (or the
 * solver's virtual world) and applies the ground filter's include and exclude lists in {@link #groundTop}.
 */
public interface TerrainView {
	/**
	 * Height of the walkable top of the block at (x, y, z), measured up from the block's bottom: 1 for a full
	 * block, 0.5 for a bottom slab, 1.5 for a fence. Returns NaN if the block is not ground, either because it has
	 * no solid top or because the ground filter excludes it.
	 */
	double groundTop(int x, int y, int z);

	/** Whether the block has collision that would block a body standing in its space, for headroom checks. */
	boolean obstructs(int x, int y, int z);

	/** Height of the fluid surface in the block, measured up from its bottom, or NaN if there is no fluid. */
	double fluidTop(int x, int y, int z);
}
