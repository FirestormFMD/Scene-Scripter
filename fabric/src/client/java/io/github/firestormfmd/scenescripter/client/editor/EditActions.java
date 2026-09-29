package io.github.firestormfmd.scenescripter.client.editor;

import java.util.Optional;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;
import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.ChannelSpec;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Turns editor gestures into edit ops and sends them to the server.
 */
public final class EditActions {
	private EditActions() {
	}

	public static Optional<Scene> scene() {
		return ClientScene.scene();
	}

	public static void addObject(String entityType, Vec3 at) {
		scene().ifPresent(scene -> {
			String id = freshId(scene, "o");
			SceneObject o = new SceneObject(id, prettyName(entityType), entityType);
			o.channel(BuiltInChannels.POSITION).setDefaultValue(at);
			o.channel(BuiltInChannels.BODY_YAW).setDefaultValue(0.0);
			if (entityType.equals("minecraft:mannequin")) {
				o.appearance().put("skin", "Steve");
			}
			ClientNet.edit(new Edits.AddObject(o));
			EditorState.selectObject(id);
		});
	}

	/** Picks an ID not used yet. The scene's counter lives on the server, so this probes for a free number. */
	public static String freshId(Scene scene, String prefix) {
		for (int n = scene.objects().size() + scene.paths().size() + 1; ; n++) {
			String id = prefix + n;
			if (scene.object(id).isEmpty() && scene.path(id).isEmpty() && scene.findEventOwner(id).isEmpty()) {
				return id;
			}
		}
	}

	public static String prettyName(String entityType) {
		String path = entityType.contains(":") ? entityType.substring(entityType.indexOf(':') + 1) : entityType;
		if (path.equals("mannequin")) {
			return "Player";
		}
		String[] words = path.split("_");
		StringBuilder b = new StringBuilder();
		for (String w : words) {
			if (!w.isEmpty()) {
				b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
			}
		}
		return b.toString().trim();
	}

	/** Keys a value on the selected object at the playhead. */
	public static <T> void key(SceneObject o, ChannelSpec<T> spec, T value) {
		Interpolation interp = spec.type().interpolates() ? Interpolation.LINEAR : Interpolation.STEP;
		ClientNet.edit(new Edits.SetKeyframe(o.id(), spec.name(), Keyframe.of(ClientScene.tick(), value, interp)));
	}

	/** Keys a value on any channel by name, typed at runtime. */
	public static void keyRaw(SceneObject o, String channel, Object value, boolean stepped) {
		ClientNet.edit(new Edits.SetKeyframe(o.id(), channel, new Keyframe<>(ClientScene.tick(), value,
				stepped ? Interpolation.STEP : Interpolation.LINEAR, null, null)));
	}

	public static <T> T valueNow(SceneObject o, ChannelSpec<T> spec) {
		Optional<Channel<?>> ch = o.channel(spec.name());
		return ch.isEmpty() ? spec.defaultValue() : spec.type().cast(ch.get().valueAt(ClientScene.tick()));
	}

	public static boolean hasKeyNow(SceneObject o, String channel) {
		return o.channel(channel).map(ch -> ch.keyAt(ClientScene.tick()).isPresent()).orElse(false);
	}

	public static void deleteKey(SceneObject o, String channel, int tick) {
		ClientNet.edit(new Edits.RemoveKeyframe(o.id(), channel, tick));
	}

	/** Moves a keyframe to another tick as one undo step. */
	public static void moveKey(SceneObject o, String channel, int from, int to) {
		o.channel(channel).flatMap(ch -> ch.keyAt(from)).ifPresent(k -> {
			if (from == to) {
				return;
			}
			ClientNet.edit(new Edits.Composite("Move keyframe", java.util.List.of(
					new Edits.RemoveKeyframe(o.id(), channel, from),
					new Edits.SetKeyframe(o.id(), channel, k.withTick(to).detached()))));
		});
	}

	/** Cycles a keyframe through the interpolation modes. */
	public static void cycleInterpolation(SceneObject o, String channel, int tick) {
		o.channel(channel).flatMap(ch -> ch.keyAt(tick)).ifPresent(k -> {
			Interpolation[] all = Interpolation.values();
			Interpolation next = all[(k.interpolation().ordinal() + 1) % all.length];
			ClientNet.edit(new Edits.SetKeyframe(o.id(), channel,
					new Keyframe<>(k.tick(), k.value(), next, k.handles(), null)));
		});
	}

	public static void rename(SceneObject o, String name) {
		if (!name.isBlank()) {
			ClientNet.edit(new Edits.RenameObject(o.id(), name.trim()));
		}
	}

	public static void delete(SceneObject o) {
		ClientNet.edit(new Edits.RemoveObject(o.id()));
		EditorState.selectObject(null);
	}

	/** Applies a change to a copy of the object and sends it as one replace edit. */
	public static void change(SceneObject o, String label, java.util.function.Consumer<SceneObject> change) {
		SceneObject copy = o.copy();
		change.accept(copy);
		ClientNet.edit(new Edits.ReplaceObject(copy, label));
	}
}
