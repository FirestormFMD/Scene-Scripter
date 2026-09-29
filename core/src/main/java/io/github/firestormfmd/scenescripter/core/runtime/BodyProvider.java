package io.github.firestormfmd.scenescripter.core.runtime;

import io.github.firestormfmd.scenescripter.core.path.BodySettings;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Supplies the size and movement limits of an object's entity type. The Minecraft side reads them from the
 * entity type and its default attributes.
 */
@FunctionalInterface
public interface BodyProvider {
	BodyProvider PLAYER_SIZED = o -> BodySettings.PLAYER;

	BodySettings bodyFor(SceneObject object);
}
