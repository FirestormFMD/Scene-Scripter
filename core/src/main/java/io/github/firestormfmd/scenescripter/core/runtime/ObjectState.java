package io.github.firestormfmd.scenescripter.core.runtime;

import java.util.Map;

import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.Gait;

/**
 * Everything an object looks like on one tick. The Minecraft side copies this onto the object's actor entity.
 *
 * @param exists whether the object is in the scene at this tick
 * @param bodyYaw facing of the body in Minecraft degrees
 * @param headYaw absolute facing of the head in Minecraft degrees
 * @param pose pose name from the {@code pose} channel, such as {@code standing} or {@code sleeping}
 * @param moving travelling along a motion path this tick
 * @param ticksDead ticks since {@code dead} last became true, or -1 while alive
 * @param equipment item per equipment slot ({@code mainhand}, {@code offhand}, {@code head}, {@code chest},
 *                  {@code legs}, {@code feet}); empty strings mean no item
 * @param vehicle ID of the object this one rides, or empty
 * @param useItem hand using its item ({@code main} or {@code off}), or empty
 * @param extra values of all other channels: mob-specific ones and custom variables
 */
public record ObjectState(
		String objectId,
		boolean exists,
		Vec3 position,
		float bodyYaw,
		float headYaw,
		float headPitch,
		boolean onGround,
		boolean moving,
		boolean swimming,
		Gait gait,
		String pose,
		boolean sneaking,
		boolean sprinting,
		boolean onFire,
		boolean glowing,
		boolean invisible,
		double scale,
		String customName,
		boolean nameVisible,
		Map<String, String> equipment,
		double health,
		boolean dead,
		int ticksDead,
		boolean ambientSounds,
		boolean silent,
		String vehicle,
		String useItem,
		Map<String, Object> extra) {
	public ObjectState {
		equipment = Map.copyOf(equipment);
		extra = Map.copyOf(extra);
	}
}
