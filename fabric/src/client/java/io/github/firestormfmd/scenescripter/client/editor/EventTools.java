package io.github.firestormfmd.scenescripter.client.editor;

import java.util.Map;

import net.minecraft.world.entity.Entity;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Adds events from the inspector's event buttons.
 */
final class EventTools {
	private EventTools() {
	}

	static void add(SceneObject o, String type, int tick) {
		Scene scene = ClientScene.scene().orElse(null);
		if (scene == null) {
			return;
		}
		switch (type) {
			case "die" -> ClientNet.edit(new Edits.SetKeyframe(o.id(), BuiltInChannels.DEAD.name(),
					Keyframe.of(tick, true, Interpolation.STEP)));
			case "revive" -> ClientNet.edit(new Edits.SetKeyframe(o.id(), BuiltInChannels.DEAD.name(),
					Keyframe.of(tick, false, Interpolation.STEP)));
			case "attack" -> {
				String target = nearestOther(scene, o);
				String id = EditActions.freshId(scene, "e");
				ClientNet.edit(new Edits.AddEvent(o.id(), new SceneEvent(id, tick, "attack", target, Map.of("hand", "main"), null)));
			}
			default -> {
				String id = EditActions.freshId(scene, "e");
				ClientNet.edit(new Edits.AddEvent(o.id(), new SceneEvent(id, tick, type, null, Map.of(), null)));
			}
		}
	}

	/** The closest other actor right now, the usual target of an attack placed by hand. */
	private static String nearestOther(Scene scene, SceneObject o) {
		Entity self = ClientScene.actor(o.id()).orElse(null);
		if (self == null) {
			return null;
		}
		String best = null;
		double bestDist = Double.MAX_VALUE;
		for (SceneObject other : scene.objects()) {
			if (other.id().equals(o.id())) {
				continue;
			}
			Entity e = ClientScene.actor(other.id()).orElse(null);
			if (e != null) {
				double d = e.distanceToSqr(self);
				if (d < bestDist) {
					bestDist = d;
					best = other.id();
				}
			}
		}
		return best;
	}
}
