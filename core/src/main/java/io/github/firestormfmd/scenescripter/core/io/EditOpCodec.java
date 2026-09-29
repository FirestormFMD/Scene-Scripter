package io.github.firestormfmd.scenescripter.core.io;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.anim.ValueType;
import io.github.firestormfmd.scenescripter.core.edit.EditOp;
import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.math.BlockBox;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;

/**
 * Converts edit ops to and from JSON so the editor can send them to the server.
 */
public final class EditOpCodec {
	private EditOpCodec() {
	}

	public static String write(EditOp op) {
		return toJson(op).toString();
	}

	public static EditOp read(String json) throws SceneFormatException {
		try {
			return fromJson(JsonParser.parseString(json).getAsJsonObject());
		} catch (JsonParseException | IllegalArgumentException | IllegalStateException | NullPointerException
				| UnsupportedOperationException | ClassCastException e) {
			throw new SceneFormatException("Bad edit: " + e.getMessage(), e);
		}
	}

	public static JsonObject toJson(EditOp op) {
		JsonObject o = new JsonObject();
		switch (op) {
			case Edits.NoOp n -> {
				o.addProperty("op", "noop");
				o.addProperty("label", n.label());
			}
			case Edits.Composite c -> {
				o.addProperty("op", "composite");
				o.addProperty("label", c.label());
				JsonArray ops = new JsonArray();
				c.ops().forEach(child -> ops.add(toJson(child)));
				o.add("ops", ops);
			}
			case Edits.AddObject a -> {
				o.addProperty("op", "add_object");
				o.add("object", SceneCodec.object(a.object()));
				o.addProperty("index", a.index());
			}
			case Edits.RemoveObject r -> {
				o.addProperty("op", "remove_object");
				o.addProperty("id", r.objectId());
			}
			case Edits.RenameObject r -> {
				o.addProperty("op", "rename_object");
				o.addProperty("id", r.objectId());
				o.addProperty("name", r.name());
			}
			case Edits.ReplaceObject r -> {
				o.addProperty("op", "replace_object");
				o.add("object", SceneCodec.object(r.object()));
				o.addProperty("label", r.label());
			}
			case Edits.SetKeyframe k -> {
				o.addProperty("op", "set_keyframe");
				o.addProperty("object", k.objectId());
				o.addProperty("channel", k.channel());
				o.addProperty("type", typeOf(k.key()).id());
				o.add("key", keyframe(k.key()));
			}
			case Edits.RemoveKeyframe k -> {
				o.addProperty("op", "remove_keyframe");
				o.addProperty("object", k.objectId());
				o.addProperty("channel", k.channel());
				o.addProperty("t", k.tick());
			}
			case Edits.AddEvent e -> {
				o.addProperty("op", "add_event");
				o.addProperty("object", e.objectId());
				o.add("event", SceneCodec.event(e.event()));
			}
			case Edits.RemoveEvent e -> {
				o.addProperty("op", "remove_event");
				o.addProperty("object", e.objectId());
				o.addProperty("event", e.eventId());
			}
			case Edits.AddPath a -> {
				o.addProperty("op", "add_path");
				o.add("path", SceneCodec.path(a.path()));
				o.addProperty("index", a.index());
			}
			case Edits.RemovePath r -> {
				o.addProperty("op", "remove_path");
				o.addProperty("id", r.pathId());
			}
			case Edits.ReplacePath r -> {
				o.addProperty("op", "replace_path");
				o.add("path", SceneCodec.path(r.path()));
				o.addProperty("label", r.label());
			}
			case Edits.SetPathPoints p -> {
				o.addProperty("op", "set_path_points");
				o.addProperty("path", p.pathId());
				JsonArray points = new JsonArray();
				p.points().forEach(pt -> points.add(SceneCodec.pathPoint(pt)));
				o.add("points", points);
				o.addProperty("label", p.label());
			}
			case Edits.SetSceneHeader h -> {
				o.addProperty("op", "set_scene_header");
				o.addProperty("name", h.name());
				o.addProperty("length", h.length());
				o.add("origin", SceneCodec.blockPosJson(h.origin()));
				if (h.bounds() != null) {
					JsonObject b = new JsonObject();
					b.add("min", SceneCodec.blockPosJson(h.bounds().min()));
					b.add("max", SceneCodec.blockPosJson(h.bounds().max()));
					o.add("bounds", b);
				}
				o.add("settings", SceneCodec.settings(h.settings()));
			}
			default -> throw new IllegalArgumentException("Cannot encode edit " + op.getClass().getSimpleName());
		}
		return o;
	}

	public static EditOp fromJson(JsonObject o) throws SceneFormatException {
		String kind = o.get("op").getAsString();
		return switch (kind) {
			case "noop" -> new Edits.NoOp(o.get("label").getAsString());
			case "composite" -> {
				List<EditOp> ops = new ArrayList<>();
				for (JsonElement e : o.getAsJsonArray("ops")) {
					ops.add(fromJson(e.getAsJsonObject()));
				}
				yield new Edits.Composite(o.get("label").getAsString(), ops);
			}
			case "add_object" -> new Edits.AddObject(SceneCodec.readObject(o.getAsJsonObject("object")),
					o.get("index").getAsInt());
			case "remove_object" -> new Edits.RemoveObject(o.get("id").getAsString());
			case "rename_object" -> new Edits.RenameObject(o.get("id").getAsString(), o.get("name").getAsString());
			case "replace_object" -> new Edits.ReplaceObject(SceneCodec.readObject(o.getAsJsonObject("object")),
					o.get("label").getAsString());
			case "set_keyframe" -> new Edits.SetKeyframe(o.get("object").getAsString(), o.get("channel").getAsString(),
					SceneCodec.readKeyframe(ValueType.byId(o.get("type").getAsString()), o.getAsJsonObject("key")));
			case "remove_keyframe" -> new Edits.RemoveKeyframe(o.get("object").getAsString(),
					o.get("channel").getAsString(), o.get("t").getAsInt());
			case "add_event" -> new Edits.AddEvent(o.get("object").getAsString(),
					SceneCodec.readEvent(o.getAsJsonObject("event")));
			case "remove_event" -> new Edits.RemoveEvent(o.get("object").getAsString(), o.get("event").getAsString());
			case "add_path" -> new Edits.AddPath(SceneCodec.readPath(o.getAsJsonObject("path")), o.get("index").getAsInt());
			case "remove_path" -> new Edits.RemovePath(o.get("id").getAsString());
			case "replace_path" -> new Edits.ReplacePath(SceneCodec.readPath(o.getAsJsonObject("path")),
					o.get("label").getAsString());
			case "set_path_points" -> {
				List<PathPoint> points = new ArrayList<>();
				for (JsonElement e : o.getAsJsonArray("points")) {
					points.add(SceneCodec.readPathPoint(e.getAsJsonObject()));
				}
				yield new Edits.SetPathPoints(o.get("path").getAsString(), points, o.get("label").getAsString());
			}
			case "set_scene_header" -> {
				BlockBox bounds = null;
				if (o.has("bounds")) {
					JsonObject b = o.getAsJsonObject("bounds");
					bounds = new BlockBox(SceneCodec.readBlockPosJson(b.get("min")), SceneCodec.readBlockPosJson(b.get("max")));
				}
				yield new Edits.SetSceneHeader(o.get("name").getAsString(), o.get("length").getAsInt(),
						SceneCodec.readBlockPosJson(o.get("origin")), bounds,
						SceneCodec.readSettings(o.getAsJsonObject("settings")));
			}
			default -> throw new SceneFormatException("Unknown edit: " + kind);
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> JsonObject keyframe(Keyframe<T> key) {
		return SceneCodec.keyframe((ValueType<T>) typeOf(key), key);
	}

	private static ValueType<?> typeOf(Keyframe<?> key) {
		for (ValueType<?> t : ValueType.ALL) {
			if (t.javaType().isInstance(key.value()) && !(t == ValueType.ENUM || t == ValueType.TEXT || t == ValueType.ITEM)) {
				return t;
			}
		}
		// Strings are stored the same way whichever string type the channel uses.
		return ValueType.TEXT;
	}
}
