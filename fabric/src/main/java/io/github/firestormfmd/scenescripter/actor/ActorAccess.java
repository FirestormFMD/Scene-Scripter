package io.github.firestormfmd.scenescripter.actor;

import org.jspecify.annotations.Nullable;

/**
 * Added to every entity by mixin. An entity with an object ID is an actor: a scene object's body in the world.
 */
public interface ActorAccess {
	@Nullable String scenescripter$objectId();

	void scenescripter$setObjectId(@Nullable String objectId);

	/** Items a scene explosion dropped: real and ticking, but never saved, so they can't outlive the scene. */
	boolean scenescripter$isSceneDrop();

	void scenescripter$setSceneDrop(boolean sceneDrop);
}
