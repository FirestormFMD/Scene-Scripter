package io.github.firestormfmd.scenescripter.compat;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The game calls that differ between the Minecraft versions Scene Scripter builds for. Each version has its own copy
 * of this class under {@code src/versions/<version>}; this one is for 26.2.
 */
public final class Compat {
	/** The enderman's class. */
	public static final Class<? extends Entity> ENDERMAN = EnderMan.class;

	private Compat() {
	}

	/** Makes an entity immune to damage until this is called again, or lifts that. */
	public static void setInvulnerable(Entity entity, boolean invulnerable) {
		entity.setInvulnerable(invulnerable);
	}

	/** Whether an entity is immune to damage until told otherwise. */
	public static boolean isInvulnerable(Entity entity) {
		return entity.isInvulnerable();
	}

	/** Swings an arm the way an attack does, shown to everyone watching. */
	public static void swing(LivingEntity entity, InteractionHand hand) {
		entity.swing(hand, true);
	}

	public static void setCarriedBlock(Entity enderman, @Nullable BlockState block) {
		((EnderMan) enderman).setCarriedBlock(block);
	}

	public static boolean isScreaming(Entity enderman) {
		return ((EnderMan) enderman).isCreepy();
	}

	/** The total ticks on the level's day clock, or -1 when its dimension has none. */
	public static long dayClockTicks(ServerLevel level) {
		return level.dimensionType().defaultClock().map(clock -> level.clockManager().getTotalTicks(clock)).orElse(-1L);
	}
}
