package io.github.firestormfmd.scenescripter.core.capture;

import java.util.Map;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * One tick of a performance: where the performer was and what they were doing.
 *
 * @param tick scene tick
 * @param headYaw absolute head facing in Minecraft degrees
 * @param equipment item per slot, as in {@code ObjectState}, plus {@link #USE_ITEM}: the hand drawing a bow,
 *     eating or raising a shield ({@code main} or {@code off}), when there is one
 */
public record CaptureSample(int tick, Vec3 pos, float bodyYaw, float headYaw, float headPitch, boolean onGround,
		boolean sneaking, boolean sprinting, boolean swimming, String pose, Map<String, String> equipment) {
	/** Key in {@link #equipment()} for the hand in use; stored with the equipment since it changes as rarely. */
	public static final String USE_ITEM = "use_item";

	public CaptureSample {
		equipment = Map.copyOf(equipment);
	}
}
