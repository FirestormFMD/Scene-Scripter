package io.github.firestormfmd.scenescripter.core.crowd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * How a crowd is laid out around its leader. Offsets are in the leader's frame: x to its right, z behind it.
 */
public enum Formation {
	/** Side by side, like a rank. */
	LINE,
	/** Rows and columns behind the leader, like a marching block. */
	GRID,
	/** A ring around the leader's position. */
	CIRCLE,
	/** Loosely spread out, never closer than the spacing. */
	SCATTER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Formation byId(String id) {
		return valueOf(id.trim().toUpperCase(Locale.ROOT));
	}

	/** Offsets of {@code count} members, the first being the leader's own place for line and grid. */
	public List<Vec3> offsets(int count, double spacing, long seed) {
		List<Vec3> out = new ArrayList<>(count);
		switch (this) {
			case LINE -> {
				for (int i = 0; i < count; i++) {
					out.add(new Vec3((i - (count - 1) / 2.0) * spacing, 0, 0));
				}
			}
			case GRID -> {
				int cols = (int) Math.ceil(Math.sqrt(count));
				for (int i = 0; i < count; i++) {
					int row = i / cols;
					int col = i % cols;
					out.add(new Vec3((col - (cols - 1) / 2.0) * spacing, 0, row * spacing));
				}
			}
			case CIRCLE -> {
				// Neighbours are a chord apart, so the chord, not the arc, must equal the spacing.
				double radius = count < 2 ? 0 : Math.max(spacing, spacing / (2 * Math.sin(Math.PI / count)));
				for (int i = 0; i < count; i++) {
					double a = 2 * Math.PI * i / count;
					out.add(new Vec3(Math.cos(a) * radius, 0, Math.sin(a) * radius));
				}
			}
			case SCATTER -> {
				Random random = new Random(seed);
				double side = Math.sqrt(count) * spacing * 1.5;
				for (int i = 0; i < count; i++) {
					Vec3 best = null;
					for (int attempt = 0; attempt < 60; attempt++) {
						Vec3 p = new Vec3((random.nextDouble() - 0.5) * side, 0, random.nextDouble() * side);
						final Vec3 cand = p;
						if (out.stream().allMatch(q -> q.distanceTo(cand) >= spacing)) {
							best = p;
							break;
						}
						if (best == null) {
							best = p;
						}
					}
					out.add(best);
				}
			}
		}
		return out;
	}
}
