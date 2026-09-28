package io.github.firestormfmd.scenescripter.core.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * The edit ops the editor uses.
 */
public final class Edits {
	private Edits() {
	}

	/** An edit that changes nothing, returned as the inverse of edits that turned out to have no effect. */
	public record NoOp(String label) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			return this;
		}
	}

	/** Several edits applied as one undo step. */
	public record Composite(String label, List<EditOp> ops) implements EditOp {
		public Composite {
			ops = List.copyOf(ops);
		}

		@Override
		public EditOp apply(Scene scene) {
			List<EditOp> inverses = new ArrayList<>();
			try {
				for (EditOp op : ops) {
					inverses.add(op.apply(scene));
				}
			} catch (RuntimeException e) {
				// Roll back what was applied so the scene is left unchanged.
				for (int i = inverses.size() - 1; i >= 0; i--) {
					inverses.get(i).apply(scene);
				}
				throw e;
			}
			return new Composite(label, inverses.reversed());
		}
	}

	public record AddObject(SceneObject object, int index) implements EditOp {
		public AddObject {
			object = object.copy();
		}

		public AddObject(SceneObject object) {
			this(object, -1);
		}

		@Override
		public EditOp apply(Scene scene) {
			scene.addObject(object.copy(), index);
			return new RemoveObject(object.id());
		}

		@Override
		public String label() {
			return "Add " + object.name();
		}
	}

	public record RemoveObject(String objectId) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			int index = scene.indexOfObject(objectId);
			SceneObject removed = scene.removeObject(objectId).orElseThrow(() -> missing("object", objectId));
			return new AddObject(removed, index);
		}

		@Override
		public String label() {
			return "Delete object";
		}
	}

	public record RenameObject(String objectId, String name) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			SceneObject o = object(scene, objectId);
			String old = o.name();
			o.setName(name);
			return new RenameObject(objectId, old);
		}

		@Override
		public String label() {
			return "Rename object";
		}
	}

	/** Adds a keyframe or replaces the one at the same tick. Built-in channels are created on first use. */
	public record SetKeyframe(String objectId, String channel, Keyframe<?> key) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			SceneObject o = object(scene, objectId);
			Channel<?> ch = o.channel(channel).orElseGet(() -> BuiltInChannels.byName(channel)
					.map(o::channel)
					.orElseThrow(() -> missing("channel", channel)));
			Optional<? extends Keyframe<?>> previous = ch.putUnchecked(key);
			return previous.<EditOp>map(p -> new SetKeyframe(objectId, channel, p))
					.orElseGet(() -> new RemoveKeyframe(objectId, channel, key.tick()));
		}

		@Override
		public String label() {
			return "Set keyframe";
		}
	}

	public record RemoveKeyframe(String objectId, String channel, int tick) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			Channel<?> ch = object(scene, objectId).channel(channel).orElseThrow(() -> missing("channel", channel));
			Optional<? extends Keyframe<?>> removed = ch.remove(tick);
			return removed.<EditOp>map(k -> new SetKeyframe(objectId, channel, k))
					.orElseGet(() -> new NoOp(label()));
		}

		@Override
		public String label() {
			return "Delete keyframe";
		}
	}

	public record AddEvent(String objectId, SceneEvent event) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			object(scene, objectId).addEvent(event);
			return new RemoveEvent(objectId, event.id());
		}

		@Override
		public String label() {
			return "Add " + event.type();
		}
	}

	public record RemoveEvent(String objectId, String eventId) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			SceneEvent removed = object(scene, objectId).removeEvent(eventId)
					.orElseThrow(() -> missing("event", eventId));
			return new AddEvent(objectId, removed);
		}

		@Override
		public String label() {
			return "Delete event";
		}
	}

	public record AddPath(MotionPath path, int index) implements EditOp {
		public AddPath {
			path = path.copy();
		}

		public AddPath(MotionPath path) {
			this(path, -1);
		}

		@Override
		public EditOp apply(Scene scene) {
			scene.addPath(path.copy(), index);
			return new RemovePath(path.id());
		}

		@Override
		public String label() {
			return "Add path";
		}
	}

	public record RemovePath(String pathId) implements EditOp {
		@Override
		public EditOp apply(Scene scene) {
			int index = scene.indexOfPath(pathId);
			MotionPath removed = scene.removePath(pathId).orElseThrow(() -> missing("path", pathId));
			return new AddPath(removed, index);
		}

		@Override
		public String label() {
			return "Delete path";
		}
	}

	/** Replaces all control points of a path. Covers moving, inserting and deleting points. */
	public record SetPathPoints(String pathId, List<PathPoint> points, String label) implements EditOp {
		public SetPathPoints {
			points = List.copyOf(points);
		}

		@Override
		public EditOp apply(Scene scene) {
			MotionPath path = scene.path(pathId).orElseThrow(() -> missing("path", pathId));
			List<PathPoint> old = List.copyOf(path.points());
			path.points().clear();
			path.points().addAll(points);
			return new SetPathPoints(pathId, old, label);
		}
	}

	private static SceneObject object(Scene scene, String id) {
		return scene.object(id).orElseThrow(() -> missing("object", id));
	}

	private static IllegalArgumentException missing(String what, String id) {
		return new IllegalArgumentException("No " + what + " named " + id);
	}
}
