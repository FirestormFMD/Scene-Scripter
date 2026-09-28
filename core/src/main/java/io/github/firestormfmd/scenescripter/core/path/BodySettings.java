package io.github.firestormfmd.scenescripter.core.path;

/**
 * The physical size and movement limits of the object following a path. The Minecraft side fills this in from
 * the entity type's dimensions and attributes.
 *
 * @param width hitbox width in blocks
 * @param height hitbox height in blocks
 * @param stepHeight highest rise walked up without jumping (vanilla {@code step_height} attribute)
 * @param jumpVelocity upward speed at takeoff in blocks per tick (vanilla {@code jump_strength} attribute)
 * @param turnRate most the body can turn in one tick, in degrees
 */
public record BodySettings(double width, double height, double stepHeight, double jumpVelocity, double turnRate) {
	public static final BodySettings PLAYER = new BodySettings(0.6, 1.8, 0.6, JumpPhysics.VANILLA_JUMP_VELOCITY, 45);

	public BodySettings {
		if (!(width > 0) || !(height > 0)) {
			throw new IllegalArgumentException("Body size must be positive");
		}
		if (stepHeight < 0 || !(jumpVelocity > 0) || !(turnRate > 0)) {
			throw new IllegalArgumentException("Invalid movement limits");
		}
	}

	public BodySettings withJumpHeight(double blocks) {
		return new BodySettings(width, height, stepHeight, JumpPhysics.velocityForHeight(blocks), turnRate);
	}
}
