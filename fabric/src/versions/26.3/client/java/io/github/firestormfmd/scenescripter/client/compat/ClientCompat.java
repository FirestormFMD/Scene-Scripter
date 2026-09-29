package io.github.firestormfmd.scenescripter.client.compat;

import java.lang.reflect.Field;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

import io.github.firestormfmd.scenescripter.SceneScripter;

/**
 * The client calls that differ between the Minecraft versions Scene Scripter builds for. Each version has its own copy
 * of this class under {@code src/versions/<version>}; this one is for 26.3.
 */
public final class ClientCompat {
	/** The input type of keyboard keys, for key mappings. */
	public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;

	// A swing's progress is private to the entity's swing state. The game runs with Mojang's names, so the fields are
	// found by name; if they ever move, scrubbed frames simply show no swing.
	private static final Field SWING_STATE = field(LivingEntity.class, "swingState");
	private static final Field TICKS = field(LivingEntity.SwingState.class, "ticks");
	private static final Field ANIMATION = field(LivingEntity.SwingState.class, "animation");
	private static final Field OLD_ANIMATION = field(LivingEntity.SwingState.class, "oldAnimation");

	private ClientCompat() {
	}

	/** Shows an arm swing that started {@code ticksSince} ticks ago, for a scrubbed frame. */
	public static void showSwing(LivingEntity living, int ticksSince) {
		if (SWING_STATE == null || TICKS == null || ANIMATION == null || OLD_ANIMATION == null) {
			return;
		}
		SwingAnimation animation = living.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation();
		int duration = Math.max(1, animation.duration());
		float progress = Math.min(1f, (float) ticksSince / duration);
		try {
			LivingEntity.SwingState state = (LivingEntity.SwingState) SWING_STATE.get(living);
			state.startIfAble(InteractionHand.MAIN_HAND, animation, duration);
			TICKS.setInt(state, ticksSince);
			ANIMATION.setFloat(state, progress);
			OLD_ANIMATION.setFloat(state, progress);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// leave the swing as vanilla has it
		}
	}

	private static Field field(Class<?> owner, String name) {
		try {
			Field f = owner.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (ReflectiveOperationException | RuntimeException e) {
			SceneScripter.LOGGER.warn("Scrubbed frames won't show arm swings: {}.{} not found", owner.getSimpleName(), name);
			return null;
		}
	}
}
