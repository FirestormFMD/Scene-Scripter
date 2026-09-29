package io.github.firestormfmd.scenescripter.core.solve;

import java.util.Map;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * Vanilla combat numbers and formulas, so auto-resolved fights deal the damage and knockback the real game would.
 */
public final class VanillaCombat {
	/** Ticks after a hit during which further hits are ignored unless they deal more damage. */
	public static final int HIT_COOLDOWN = 10;
	public static final double CRIT_MULTIPLIER = 1.5;
	/** Knockback every hit applies, before sprinting or enchantments. */
	public static final double BASE_KNOCKBACK = 0.4;
	/** Player reach for hitting entities ({@code entity_interaction_range}). */
	public static final double PLAYER_REACH = 3.0;
	/** How far a mob's melee hitbox reaches past its own box: sqrt(2.04) - 0.6. */
	public static final double MOB_MELEE_REACH = Math.sqrt(2.04) - 0.6;

	/** Extra attack damage a held item adds on top of the attacker's base damage. */
	private static final Map<String, Double> WEAPON_BONUS = Map.ofEntries(
			Map.entry("minecraft:wooden_sword", 3.0), Map.entry("minecraft:stone_sword", 4.0),
			Map.entry("minecraft:copper_sword", 4.0), Map.entry("minecraft:iron_sword", 5.0),
			Map.entry("minecraft:golden_sword", 3.0), Map.entry("minecraft:diamond_sword", 6.0),
			Map.entry("minecraft:netherite_sword", 7.0),
			Map.entry("minecraft:wooden_axe", 6.0), Map.entry("minecraft:stone_axe", 8.0),
			Map.entry("minecraft:copper_axe", 8.0), Map.entry("minecraft:iron_axe", 8.0),
			Map.entry("minecraft:golden_axe", 6.0), Map.entry("minecraft:diamond_axe", 8.0),
			Map.entry("minecraft:netherite_axe", 9.0),
			Map.entry("minecraft:wooden_shovel", 1.5), Map.entry("minecraft:stone_shovel", 2.5),
			Map.entry("minecraft:iron_shovel", 3.5), Map.entry("minecraft:diamond_shovel", 4.5),
			Map.entry("minecraft:netherite_shovel", 5.5), Map.entry("minecraft:golden_shovel", 1.5),
			Map.entry("minecraft:wooden_pickaxe", 1.0), Map.entry("minecraft:stone_pickaxe", 2.0),
			Map.entry("minecraft:iron_pickaxe", 3.0), Map.entry("minecraft:diamond_pickaxe", 4.0),
			Map.entry("minecraft:netherite_pickaxe", 5.0), Map.entry("minecraft:golden_pickaxe", 1.0),
			Map.entry("minecraft:trident", 8.0), Map.entry("minecraft:mace", 5.0));

	/** Armor points and toughness per item: {armor, toughness}. */
	private static final Map<String, double[]> ARMOR = Map.ofEntries(
			Map.entry("minecraft:leather_helmet", new double[] {1, 0}), Map.entry("minecraft:leather_chestplate", new double[] {3, 0}),
			Map.entry("minecraft:leather_leggings", new double[] {2, 0}), Map.entry("minecraft:leather_boots", new double[] {1, 0}),
			Map.entry("minecraft:chainmail_helmet", new double[] {2, 0}), Map.entry("minecraft:chainmail_chestplate", new double[] {5, 0}),
			Map.entry("minecraft:chainmail_leggings", new double[] {4, 0}), Map.entry("minecraft:chainmail_boots", new double[] {1, 0}),
			Map.entry("minecraft:iron_helmet", new double[] {2, 0}), Map.entry("minecraft:iron_chestplate", new double[] {6, 0}),
			Map.entry("minecraft:iron_leggings", new double[] {5, 0}), Map.entry("minecraft:iron_boots", new double[] {2, 0}),
			Map.entry("minecraft:golden_helmet", new double[] {2, 0}), Map.entry("minecraft:golden_chestplate", new double[] {5, 0}),
			Map.entry("minecraft:golden_leggings", new double[] {3, 0}), Map.entry("minecraft:golden_boots", new double[] {1, 0}),
			Map.entry("minecraft:diamond_helmet", new double[] {3, 2}), Map.entry("minecraft:diamond_chestplate", new double[] {8, 2}),
			Map.entry("minecraft:diamond_leggings", new double[] {6, 2}), Map.entry("minecraft:diamond_boots", new double[] {3, 2}),
			Map.entry("minecraft:netherite_helmet", new double[] {3, 3}), Map.entry("minecraft:netherite_chestplate", new double[] {8, 3}),
			Map.entry("minecraft:netherite_leggings", new double[] {6, 3}), Map.entry("minecraft:netherite_boots", new double[] {3, 3}),
			Map.entry("minecraft:turtle_helmet", new double[] {2, 0}));

	private VanillaCombat() {
	}

	/** Item ID without any component suffix such as {@code [enchantments=...]}. */
	private static String itemId(String item) {
		int bracket = item.indexOf('[');
		return bracket >= 0 ? item.substring(0, bracket) : item;
	}

	public static double weaponBonus(String heldItem) {
		return WEAPON_BONUS.getOrDefault(itemId(heldItem), 0.0);
	}

	/** Total armor points and toughness of the worn items. */
	public static double[] armor(Iterable<String> worn) {
		double armor = 0;
		double toughness = 0;
		for (String item : worn) {
			double[] a = ARMOR.get(itemId(item));
			if (a != null) {
				armor += a[0];
				toughness += a[1];
			}
		}
		return new double[] {armor, toughness};
	}

	/** Vanilla armor reduction ({@code CombatRules.getDamageAfterAbsorb}). */
	public static double afterArmor(double damage, double armor, double toughness) {
		double f = 2.0 + toughness / 4.0;
		double g = Math.clamp(armor - damage / f, armor * 0.2, 20.0);
		return damage * (1.0 - g / 25.0);
	}

	/**
	 * Horizontal and vertical displacement of a knockback, tick by tick, using vanilla velocity, gravity, air drag
	 * and ground friction. The first entry is the tick of the hit (no movement yet); the last is where it settles.
	 *
	 * @param direction horizontal direction the target is pushed, any length
	 */
	public static Vec3[] knockbackPath(Vec3 direction, double strength) {
		Vec3 d = new Vec3(direction.x(), 0, direction.z()).normalize();
		if (strength <= 0 || d.horizontalLength() < 1.0e-9) {
			return new Vec3[] {Vec3.ZERO};
		}
		double vx = d.x() * strength;
		double vz = d.z() * strength;
		double vy = Math.min(0.4, strength);
		double x = 0;
		double y = 0;
		double z = 0;
		boolean onGround = false;
		java.util.List<Vec3> out = new java.util.ArrayList<>();
		out.add(Vec3.ZERO);
		for (int i = 0; i < 60; i++) {
			x += vx;
			z += vz;
			if (!onGround) {
				y += vy;
				vy = (vy - 0.08) * 0.98;
				if (y <= 0) {
					y = 0;
					vy = 0;
					onGround = true;
				}
			}
			double friction = onGround ? 0.6 * 0.91 : 0.91;
			vx *= friction;
			vz *= friction;
			out.add(new Vec3(x, y, z));
			if (onGround && Math.abs(vx) < 0.003 && Math.abs(vz) < 0.003) {
				break;
			}
		}
		return out.toArray(new Vec3[0]);
	}
}
