package io.github.firestormfmd.scenescripter.core.solve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * The vanilla explosion, computed ahead of time. Block damage follows the game's ray algorithm (1352 rays from
 * the surface of a 16x16x16 grid, each losing strength to distance and to the resistance of what it passes
 * through), with the random ray strengths drawn from a seeded generator so the same explosion always makes the
 * same crater. Damage and knockback to objects use vanilla exposure: the share of sample points on the object's
 * box that can see the centre.
 */
public final class Explosions {
	/** Explosion power when nothing else is set. */
	public static final float TNT_POWER = 4;
	public static final float CREEPER_POWER = 3;
	public static final float CHARGED_CREEPER_POWER = 6;
	public static final float END_CRYSTAL_POWER = 6;
	public static final float FIREBALL_POWER = 1;
	/** Vanilla TNT fuse in ticks. */
	public static final int TNT_FUSE = 80;
	/** Ticks a creeper swells before it explodes. */
	public static final int CREEPER_FUSE = 30;

	private Explosions() {
	}

	/** What an explosion does to one object. */
	public record Impact(double damage, Vec3 velocity) {
	}

	/**
	 * Blocks the explosion breaks, in a stable order, following {@code ServerExplosion.calculateExplodedPositions}.
	 * Positions with nothing in them are left out.
	 */
	public static List<BlockPos> affectedBlocks(BlockWorld world, Vec3 center, float power, Random random) {
		Set<BlockPos> hit = new LinkedHashSet<>();
		for (int j = 0; j < 16; j++) {
			for (int k = 0; k < 16; k++) {
				for (int l = 0; l < 16; l++) {
					if (j != 0 && j != 15 && k != 0 && k != 15 && l != 0 && l != 15) {
						continue;
					}
					double dx = j / 15.0F * 2.0F - 1.0F;
					double dy = k / 15.0F * 2.0F - 1.0F;
					double dz = l / 15.0F * 2.0F - 1.0F;
					double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
					dx /= len;
					dy /= len;
					dz /= len;
					float strength = power * (0.7F + random.nextFloat() * 0.6F);
					double x = center.x();
					double y = center.y();
					double z = center.z();
					for (; strength > 0.0F; strength -= 0.22500001F) {
						BlockPos pos = BlockPos.containing(x, y, z);
						if (!world.inBuildHeight(pos.y())) {
							break;
						}
						float resistance = world.explosionResistance(pos);
						if (resistance >= 0) {
							strength -= (resistance + 0.3F) * 0.3F;
							if (strength > 0.0F) {
								hit.add(pos);
							}
						}
						x += dx * 0.3F;
						y += dy * 0.3F;
						z += dz * 0.3F;
					}
				}
			}
		}
		List<BlockPos> out = new ArrayList<>(hit);
		out.sort(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z));
		return out;
	}

	/**
	 * Share of an object's box that can see the explosion, from 0 to 1 ({@code Explosion.getSeenPercent}).
	 */
	public static float seenPercent(BlockWorld world, Vec3 center, Vec3 min, Vec3 max) {
		double sx = 1.0 / ((max.x() - min.x()) * 2.0 + 1.0);
		double sy = 1.0 / ((max.y() - min.y()) * 2.0 + 1.0);
		double sz = 1.0 / ((max.z() - min.z()) * 2.0 + 1.0);
		double ox = (1.0 - Math.floor(1.0 / sx) * sx) / 2.0;
		double oz = (1.0 - Math.floor(1.0 / sz) * sz) / 2.0;
		if (sx < 0 || sy < 0 || sz < 0) {
			return 0;
		}
		int visible = 0;
		int total = 0;
		for (double a = 0; a <= 1; a += sx) {
			for (double b = 0; b <= 1; b += sy) {
				for (double c = 0; c <= 1; c += sz) {
					Vec3 p = new Vec3(lerp(a, min.x(), max.x()) + ox, lerp(b, min.y(), max.y()),
							lerp(c, min.z(), max.z()) + oz);
					if (world.clip(p, center).isEmpty()) {
						visible++;
					}
					total++;
				}
			}
		}
		return total == 0 ? 0 : (float) visible / total;
	}

	/**
	 * Damage and push an explosion gives an object ({@code ServerExplosion.hurtEntities}), or null if the object is
	 * out of range.
	 *
	 * @param feet the object's position
	 * @param width the object's box width
	 * @param height the object's box height
	 * @param pushFromFeet push from the feet instead of the eyes, as vanilla does for TNT
	 * @param knockbackResistance the {@code explosion_knockback_resistance} attribute
	 */
	public static Impact impact(BlockWorld world, Vec3 center, float power, Vec3 feet, double width, double height,
			boolean pushFromFeet, double knockbackResistance) {
		double diameter = power * 2.0;
		double dist = feet.distanceTo(center) / diameter;
		if (dist > 1.0) {
			return null;
		}
		double eyeY = pushFromFeet ? feet.y() : feet.y() + height * 0.85;
		Vec3 dir = new Vec3(feet.x() - center.x(), eyeY - center.y(), feet.z() - center.z());
		double len = dir.length();
		if (len == 0) {
			return null;
		}
		dir = dir.scale(1 / len);
		Vec3 min = feet.add(-width / 2, 0, -width / 2);
		Vec3 max = feet.add(width / 2, height, width / 2);
		double seen = seenPercent(world, center, min, max);
		double exposure = (1.0 - dist) * seen;
		double damage = (float) ((exposure * exposure + exposure) / 2.0 * 7.0 * diameter + 1.0);
		double push = exposure * (1.0 - knockbackResistance);
		return new Impact(damage, dir.scale(push));
	}

	/** Fuse of a TNT block set off by another explosion: vanilla picks 10 to 29 ticks. */
	public static int chainFuse(Random random) {
		return random.nextInt(TNT_FUSE / 4) + TNT_FUSE / 8;
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}
}
