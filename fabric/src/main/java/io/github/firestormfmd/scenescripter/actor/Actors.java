package io.github.firestormfmd.scenescripter.actor;

import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

/**
 * Helpers for telling actors apart from ordinary entities.
 *
 * <p>Actors are real vanilla entities that the scene drives. They do not tick, cannot be pushed, hit, shot or
 * ridden by anything outside the scene, and are never saved with the world.
 */
public final class Actors {
	/** Distance in blocks within which actors are sent to clients and recorders. Set by the active scene. */
	private static volatile int trackingRange = 160;
	private static final ThreadLocal<Boolean> SCENE_ACTION = ThreadLocal.withInitial(() -> false);

	private Actors() {
	}

	public static boolean isActor(@Nullable Entity entity) {
		return entity != null && ((ActorAccess) entity).scenescripter$objectId() != null;
	}

	public static @Nullable String objectId(Entity entity) {
		return ((ActorAccess) entity).scenescripter$objectId();
	}

	public static void mark(Entity entity, @Nullable String objectId) {
		((ActorAccess) entity).scenescripter$setObjectId(objectId);
	}

	public static int trackingRange() {
		return trackingRange;
	}

	public static void setTrackingRange(int blocks) {
		trackingRange = Math.max(16, blocks);
	}

	/**
	 * Runs something the scene itself does to actors, such as mounting one on another. Everything outside such a
	 * call is kept away from actors.
	 */
	public static void runAsScene(Runnable action) {
		boolean previous = SCENE_ACTION.get();
		SCENE_ACTION.set(true);
		try {
			action.run();
		} finally {
			SCENE_ACTION.set(previous);
		}
	}

	public static boolean inSceneAction() {
		return SCENE_ACTION.get();
	}
}
