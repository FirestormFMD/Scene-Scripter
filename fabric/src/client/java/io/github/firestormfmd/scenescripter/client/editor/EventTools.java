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
			case "mount" -> {
				String vehicle = nearestOther(scene, o);
				if (vehicle != null) {
					ClientNet.edit(new Edits.SetKeyframe(o.id(), BuiltInChannels.VEHICLE.name(),
							Keyframe.of(tick, vehicle, Interpolation.STEP)));
				}
			}
			case "dismount" -> ClientNet.edit(new Edits.SetKeyframe(o.id(), BuiltInChannels.VEHICLE.name(),
					Keyframe.of(tick, "", Interpolation.STEP)));
			case "shoot" -> {
				String target = nearestOther(scene, o);
				String id = EditActions.freshId(scene, "e");
				ClientNet.edit(new Edits.AddEvent(o.id(), new SceneEvent(id, tick, "shoot", target, Map.of(), null)));
			}
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

	/** Adds a mob-specific event, such as an iron golem offering a flower. */
	static void mobEvent(SceneObject o, String id, int tick) {
		ClientScene.scene().ifPresent(scene -> ClientNet.edit(new Edits.AddEvent(o.id(), new SceneEvent(
				EditActions.freshId(scene, "e"), tick, "mob_event", null, Map.of("event", id), null))));
	}

	/**
	 * Moves the object to {@code to} in one tick: its position holds until the tick before, then jumps. Objects
	 * that can show it get the vanilla teleport particles too.
	 */
	static void teleport(SceneObject o, int tick, io.github.firestormfmd.scenescripter.core.math.Vec3 to) {
		Scene scene = ClientScene.scene().orElse(null);
		var evaluator = ClientScene.evaluator().orElse(null);
		if (scene == null || evaluator == null) {
			return;
		}
		var from = evaluator.evaluate(o, Math.max(0, tick - 1)).position();
		java.util.List<io.github.firestormfmd.scenescripter.core.edit.EditOp> ops = new java.util.ArrayList<>();
		if (tick > 0) {
			ops.add(new Edits.SetKeyframe(o.id(), BuiltInChannels.POSITION.name(), Keyframe.of(tick - 1, from, Interpolation.STEP)));
		}
		ops.add(new Edits.SetKeyframe(o.id(), BuiltInChannels.POSITION.name(), Keyframe.of(tick, to, Interpolation.STEP)));
		if (io.github.firestormfmd.scenescripter.actor.MobEvents.find(o.entityType(), "teleport").isPresent()) {
			ops.add(new Edits.AddEvent(o.id(), new SceneEvent(EditActions.freshId(scene, "e"), tick, "mob_event", null,
					Map.of("event", "teleport"), null)));
		}
		ClientNet.edit(new Edits.Composite("Teleport", ops));
	}

	/** Adds a block event on the selected object for the block at {@code pos}. */
	static void block(SceneObject o, String type, net.minecraft.core.BlockPos pos, String blockState, int tick) {
		Scene scene = ClientScene.scene().orElse(null);
		if (scene == null) {
			return;
		}
		java.util.Map<String, Object> params = new java.util.HashMap<>();
		params.put("x", pos.getX());
		params.put("y", pos.getY());
		params.put("z", pos.getZ());
		if (blockState != null) {
			params.put("block", blockState);
		}
		String id = EditActions.freshId(scene, "e");
		ClientNet.edit(new Edits.AddEvent(o.id(), new SceneEvent(id, tick, type, null, params, null)));
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
