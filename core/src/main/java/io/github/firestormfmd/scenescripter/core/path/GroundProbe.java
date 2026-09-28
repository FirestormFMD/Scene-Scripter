package io.github.firestormfmd.scenescripter.core.path;

import io.github.firestormfmd.scenescripter.core.scene.FluidMode;
import io.github.firestormfmd.scenescripter.core.scene.GroundFilter;

/**
 * Finds the ground under an object's hitbox. Like a real entity, the object stands on the highest surface under
 * any part of its hitbox, so it steps up as soon as its edge reaches a block.
 */
public final class GroundProbe {
	private static final double EPS = 1.0e-6;

	private final TerrainView terrain;
	private final GroundFilter filter;
	private final BodySettings body;

	public GroundProbe(TerrainView terrain, GroundFilter filter, BodySettings body) {
		this.terrain = terrain;
		this.filter = filter;
		this.body = body;
	}

	public enum Kind {
		/** Standing on a block. */
		SOLID,
		/** Swimming at a fluid surface. */
		FLUID,
		/** No ground within the search window. */
		NONE,
		/** A fluid is in the way and the filter treats fluids as blocked. */
		BLOCKED
	}

	public record Hit(double y, Kind kind) {
		static final Hit NONE = new Hit(Double.NaN, Kind.NONE);
		static final Hit BLOCKED = new Hit(Double.NaN, Kind.BLOCKED);

		public boolean found() {
			return kind == Kind.SOLID || kind == Kind.FLUID;
		}
	}

	/**
	 * Ground under a hitbox centred at (x, z), looking from {@code nearY + searchUp} down to
	 * {@code nearY - searchDown}.
	 */
	public Hit groundAt(double x, double z, double nearY) {
		double half = body.width() / 2;
		int minX = (int) Math.floor(x - half + EPS);
		int maxX = (int) Math.floor(x + half - EPS);
		int minZ = (int) Math.floor(z - half + EPS);
		int maxZ = (int) Math.floor(z + half - EPS);

		Hit best = Hit.NONE;
		boolean blocked = false;
		for (int bx = minX; bx <= maxX; bx++) {
			for (int bz = minZ; bz <= maxZ; bz++) {
				Hit h = column(bx, bz, nearY);
				if (h.kind == Kind.BLOCKED) {
					blocked = true;
				} else if (h.found() && (!best.found() || h.y > best.y)) {
					best = h;
				}
			}
		}
		if (blocked) {
			return Hit.BLOCKED;
		}
		return best;
	}

	private Hit column(int bx, int bz, double nearY) {
		double top = nearY + filter.searchUp();
		double bottom = nearY - filter.searchDown();
		for (int by = (int) Math.floor(top); by >= (int) Math.floor(bottom); by--) {
			double fluid = terrain.fluidTop(bx, by, bz);
			if (!Double.isNaN(fluid)) {
				if (filter.fluids() == FluidMode.BLOCKED) {
					return Hit.BLOCKED;
				}
				if (filter.fluids() == FluidMode.SWIM) {
					double surface = by + fluid;
					if (surface <= top + EPS && fitsAbove(bx, by, bz, surface)) {
						return new Hit(surface, Kind.FLUID);
					}
				}
			}

			double g = terrain.groundTop(bx, by, bz);
			if (Double.isNaN(g)) {
				continue;
			}
			double surface = by + g;
			if (surface > top + EPS) {
				continue;
			}
			if (surface < bottom - EPS) {
				break;
			}
			if (fitsAbove(bx, by, bz, surface)) {
				return new Hit(surface, Kind.SOLID);
			}
		}
		return Hit.NONE;
	}

	/** Whether the hitbox fits in this column when standing at {@code surface} on the block at {@code groundY}. */
	private boolean fitsAbove(int bx, int groundY, int bz, double surface) {
		if (!filter.headroomCheck()) {
			return true;
		}
		int from = (int) Math.floor(surface + EPS);
		int to = (int) Math.floor(surface + body.height() - EPS);
		for (int by = from; by <= to; by++) {
			if (by != groundY && terrain.obstructs(bx, by, bz)) {
				return false;
			}
		}
		return true;
	}
}
