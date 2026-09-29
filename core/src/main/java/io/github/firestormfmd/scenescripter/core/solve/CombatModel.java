package io.github.firestormfmd.scenescripter.core.solve;

import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * What the solver needs to know about entity types and the world. The Minecraft side answers from default
 * attributes and a block raycast; {@link #DEFAULT} is a plain stand-in for tests.
 */
public interface CombatModel {
	/** Vanilla maximum health of the object's entity type. */
	double maxHealth(SceneObject object);

	/** The type's own {@code attack_damage} before any weapon; 1 for players. */
	double baseAttackDamage(SceneObject object);

	/** The type's {@code knockback_resistance}, from 0 (none) to 1 (immune). */
	double knockbackResistance(SceneObject object);

	/** Player-like attackers hit from their eyes with a 3-block reach and must face the target. */
	boolean isPlayerLike(SceneObject object);

	double width(SceneObject object);

	double height(SceneObject object);

	/** Whether nothing solid blocks the straight line between two points. */
	boolean lineOfSight(Vec3 from, Vec3 to);

	CombatModel DEFAULT = new CombatModel() {
		@Override
		public double maxHealth(SceneObject object) {
			return 20;
		}

		@Override
		public double baseAttackDamage(SceneObject object) {
			return isPlayerLike(object) ? 1 : 3;
		}

		@Override
		public double knockbackResistance(SceneObject object) {
			return 0;
		}

		@Override
		public boolean isPlayerLike(SceneObject object) {
			return object.entityType().equals("minecraft:mannequin") || object.entityType().equals("minecraft:player");
		}

		@Override
		public double width(SceneObject object) {
			return 0.6;
		}

		@Override
		public double height(SceneObject object) {
			return 1.8;
		}

		@Override
		public boolean lineOfSight(Vec3 from, Vec3 to) {
			return true;
		}
	};
}
