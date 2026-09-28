package io.github.firestormfmd.scenescripter.core.scene;

import java.util.List;
import java.util.Objects;

/**
 * Decides which blocks count as "ground" when snapping paths. By default any block with a solid top surface is
 * ground; {@code include} and {@code exclude} refine that by block ID ({@code minecraft:oak_leaves}) or tag
 * ({@code #minecraft:leaves}). Exclusions win over inclusions.
 *
 * @param preset name of the preset this filter came from, for display
 * @param include blocks or tags that count as ground even without a solid top, such as scaffolding
 * @param exclude blocks or tags that never count as ground
 * @param searchUp how far above the previous point's height to look for ground, in blocks
 * @param searchDown how far below the previous point's height to look for ground, in blocks
 * @param headroomCheck skip surfaces where the object's hitbox would not fit
 */
public record GroundFilter(String preset, List<String> include, List<String> exclude, FluidMode fluids,
		int searchUp, int searchDown, boolean headroomCheck) {
	/** Excludes leaves, so paths pass under trees. */
	public static final GroundFilter NATURAL_GROUND =
			new GroundFilter("natural_ground", List.of(), List.of("#minecraft:leaves"), FluidMode.SWIM, 3, 12, true);
	/** Every block with a solid top counts. */
	public static final GroundFilter ALL_SOLID =
			new GroundFilter("all_solid", List.of(), List.of(), FluidMode.SWIM, 3, 12, true);

	public GroundFilter {
		Objects.requireNonNull(preset, "preset");
		include = List.copyOf(include);
		exclude = List.copyOf(exclude);
		Objects.requireNonNull(fluids, "fluids");
		if (searchUp < 0 || searchDown < 0) {
			throw new IllegalArgumentException("Search distances cannot be negative");
		}
	}
}
