package io.github.firestormfmd.scenescripter.core.path;

import java.util.function.IntFunction;

/**
 * Terrain that changes over the scene, such as a crater a blast opens. The planner moves it to each tick before
 * probing the ground there, so an object already walking when the blast goes off falls into the hole.
 */
public final class TerrainTimeline implements TerrainView {
	private final IntFunction<TerrainView> atTick;
	private int tick;
	private TerrainView current;

	public TerrainTimeline(IntFunction<TerrainView> atTick, int startTick) {
		this.atTick = atTick;
		setTick(startTick);
	}

	/** Moves to a tick; later lookups see the blocks as they are then. */
	public void setTick(int newTick) {
		if (current == null || newTick != tick) {
			tick = newTick;
			current = atTick.apply(newTick);
		}
	}

	@Override
	public double groundTop(int x, int y, int z) {
		return current.groundTop(x, y, z);
	}

	@Override
	public boolean obstructs(int x, int y, int z) {
		return current.obstructs(x, y, z);
	}

	@Override
	public double fluidTop(int x, int y, int z) {
		return current.fluidTop(x, y, z);
	}
}
