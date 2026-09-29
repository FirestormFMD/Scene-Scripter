package io.github.firestormfmd.scenescripter.client.compat;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.world.entity.LivingEntity;

/**
 * The client calls that differ between the Minecraft versions Scene Scripter builds for. Each version has its own copy
 * of this class under {@code src/versions/<version>}; this one is for 26.2.
 */
public final class ClientCompat {
	/** The input type of keyboard keys, for key mappings. */
	public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;

	private ClientCompat() {
	}

	/** Shows an arm swing that started {@code ticksSince} ticks ago, for a scrubbed frame. */
	public static void showSwing(LivingEntity living, int ticksSince) {
		living.swinging = true;
		living.swingTime = ticksSince;
	}
}
