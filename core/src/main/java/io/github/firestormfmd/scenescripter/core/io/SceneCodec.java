package io.github.firestormfmd.scenescripter.core.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Handles;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.anim.ValueType;
import io.github.firestormfmd.scenescripter.core.math.BlockBox;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.AttackMode;
import io.github.firestormfmd.scenescripter.core.scene.CritMode;
import io.github.firestormfmd.scenescripter.core.scene.ExplosionRules;
import io.github.firestormfmd.scenescripter.core.scene.FluidMode;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.GroundFilter;
import io.github.firestormfmd.scenescripter.core.scene.InteractionRules;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.core.scene.SceneSettings;
import io.github.firestormfmd.scenescripter.core.scene.SpeedKey;
import io.github.firestormfmd.scenescripter.core.scene.TimingMode;

/**
 * Reads and writes scenes as JSON. The layout is written out by hand, not by reflection, so the file format only
 * changes when this class does.
 */
public final class SceneCodec {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private SceneCodec() {
	}

	public static String write(Scene scene) {
		return GSON.toJson(toJson(scene));
	}

	public static Scene read(String json) throws SceneFormatException {
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new SceneFormatException("Scene file is not valid JSON: " + e.getMessage(), e);
		}
		if (!root.isJsonObject()) {
			throw new SceneFormatException("Scene file must contain a JSON object");
		}
		return fromJson(root.getAsJsonObject());
	}

	// ---- Writing ----

	public static JsonObject toJson(Scene scene) {
		JsonObject o = new JsonObject();
		o.addProperty("format", Scene.FORMAT);
		o.addProperty("name", scene.name());
		o.addProperty("length", scene.length());
		o.add("origin", blockPos(scene.origin()));
		if (scene.bounds() != null) {
			JsonObject b = new JsonObject();
			b.add("min", blockPos(scene.bounds().min()));
			b.add("max", blockPos(scene.bounds().max()));
			o.add("bounds", b);
		}
		o.add("settings", settings(scene.settings()));

		JsonObject ids = new JsonObject();
		scene.idCounters().forEach(ids::addProperty);
		o.add("ids", ids);

		JsonArray paths = new JsonArray();
		scene.paths().forEach(p -> paths.add(path(p)));
		o.add("paths", paths);

		JsonArray objects = new JsonArray();
		scene.objects().forEach(obj -> objects.add(object(obj)));
		o.add("objects", objects);
		return o;
	}

	public static JsonObject settings(SceneSettings s) {
		JsonObject o = new JsonObject();
		GroundFilter g = s.groundFilter();
		JsonObject gf = new JsonObject();
		gf.addProperty("preset", g.preset());
		gf.add("include", strings(g.include()));
		gf.add("exclude", strings(g.exclude()));
		gf.addProperty("fluids", g.fluids().id());
		gf.addProperty("searchUp", g.searchUp());
		gf.addProperty("searchDown", g.searchDown());
		gf.addProperty("headroomCheck", g.headroomCheck());
		o.add("groundFilter", gf);
		o.add("rules", rules(s.rules()));
		o.addProperty("trackingRange", s.trackingRange());
		return o;
	}

	public static JsonObject rules(InteractionRules r) {
		JsonObject o = new JsonObject();
		if (r.attack() != null) o.addProperty("attack", r.attack().id());
		if (r.knockback() != null) o.addProperty("knockback", r.knockback());
		if (r.crits() != null) o.addProperty("crits", r.crits().id());
		if (r.hitCooldown() != null) o.addProperty("hitCooldown", r.hitCooldown());
		if (r.autoDeath() != null) o.addProperty("autoDeath", r.autoDeath());
		if (r.friendlyFire() != null) o.addProperty("friendlyFire", r.friendlyFire());
		ExplosionRules e = r.explosions();
		JsonObject eo = new JsonObject();
		if (e.breakBlocks() != null) eo.addProperty("breakBlocks", e.breakBlocks());
		if (e.damageObjects() != null) eo.addProperty("damageObjects", e.damageObjects());
		if (e.damageRealEntities() != null) eo.addProperty("damageRealEntities", e.damageRealEntities());
		if (e.dropItems() != null) eo.addProperty("dropItems", e.dropItems());
		if (e.fire() != null) eo.addProperty("fire", e.fire());
		if (!eo.isEmpty()) o.add("explosions", eo);
		return o;
	}

	public static JsonObject path(MotionPath p) {
		JsonObject o = new JsonObject();
		o.addProperty("id", p.id());
		o.addProperty("name", p.name());
		o.addProperty("kind", p.kind().id());
		o.addProperty("gait", p.gait().id());
		JsonObject speed = new JsonObject();
		speed.addProperty("base", p.baseSpeed());
		JsonArray keys = new JsonArray();
		for (SpeedKey k : p.speedKeys()) {
			JsonObject ko = new JsonObject();
			ko.addProperty("at", k.at());
			ko.addProperty("value", k.speed());
			keys.add(ko);
		}
		speed.add("keys", keys);
		o.add("speed", speed);

		JsonArray points = new JsonArray();
		for (PathPoint pt : p.points()) {
			points.add(pathPoint(pt));
		}
		o.add("points", points);

		JsonArray markers = new JsonArray();
		for (PathMarker m : p.markers()) {
			JsonObject mo = new JsonObject();
			mo.addProperty("at", m.at());
			mo.addProperty("type", m.kind().id());
			if (m.kind() == PathMarker.Kind.WAIT) mo.addProperty("ticks", m.ticks());
			if (m.kind() == PathMarker.Kind.GAIT) mo.addProperty("gait", m.gait().id());
			markers.add(mo);
		}
		o.add("markers", markers);
		if (p.jumpHeight() != null) o.addProperty("jumpHeight", p.jumpHeight());
		return o;
	}

	public static JsonObject object(SceneObject obj) {
		JsonObject o = new JsonObject();
		o.addProperty("id", obj.id());
		o.addProperty("name", obj.name());
		o.addProperty("type", obj.entityType());
		JsonObject appearance = new JsonObject();
		obj.appearance().forEach(appearance::addProperty);
		o.add("appearance", appearance);
		JsonArray life = new JsonArray();
		life.add(obj.spawnTick());
		life.add(obj.despawnTick());
		o.add("life", life);
		if (obj.group() != null) o.addProperty("group", obj.group());
		if (obj.generatedBy() != null) o.addProperty("gen", obj.generatedBy());

		JsonArray motion = new JsonArray();
		for (MotionClip c : obj.motion()) {
			JsonObject co = new JsonObject();
			co.addProperty("path", c.pathId());
			co.addProperty("start", c.startTick());
			co.addProperty("timing", c.timing().id());
			if (c.timing() == TimingMode.FIT) co.addProperty("end", c.endTick());
			if (c.lateralOffset() != 0) co.addProperty("offset", c.lateralOffset());
			motion.add(co);
		}
		o.add("motion", motion);

		JsonObject channels = new JsonObject();
		obj.channels().forEach((name, ch) -> channels.add(name, channel(ch)));
		o.add("channels", channels);

		JsonArray events = new JsonArray();
		for (SceneEvent e : obj.events()) {
			events.add(event(e));
		}
		o.add("events", events);

		JsonObject rules = rules(obj.rules());
		if (!rules.isEmpty()) o.add("rules", rules);
		return o;
	}

	public static <T> JsonObject keyframe(ValueType<T> type, Keyframe<T> k) {
		JsonObject ko = new JsonObject();
		ko.addProperty("t", k.tick());
		ko.add("v", value(type, k.value()));
		if (k.interpolation() != Interpolation.LINEAR) ko.addProperty("i", k.interpolation().id());
		if (k.handles() != null) {
			JsonArray h = new JsonArray();
			h.add(k.handles().inDt());
			h.add(k.handles().inDv());
			h.add(k.handles().outDt());
			h.add(k.handles().outDv());
			ko.add("h", h);
		}
		if (k.generatedBy() != null) ko.addProperty("gen", k.generatedBy());
		return ko;
	}

	public static <T> Keyframe<T> readKeyframe(ValueType<T> type, JsonObject ko) {
		Handles handles = null;
		if (ko.has("h")) {
			JsonArray h = ko.getAsJsonArray("h");
			handles = new Handles(h.get(0).getAsDouble(), h.get(1).getAsDouble(),
					h.get(2).getAsDouble(), h.get(3).getAsDouble());
		}
		return new Keyframe<>(
				req(ko, "t").getAsInt(),
				readValue(type, req(ko, "v")),
				ko.has("i") ? Interpolation.byId(str(ko, "i")) : Interpolation.LINEAR,
				handles,
				ko.has("gen") ? str(ko, "gen") : null);
	}

	public static JsonObject event(SceneEvent e) {
		JsonObject eo = new JsonObject();
		eo.addProperty("id", e.id());
		eo.addProperty("t", e.tick());
		eo.addProperty("type", e.type());
		if (e.target() != null) eo.addProperty("target", e.target());
		if (!e.params().isEmpty()) {
			JsonObject params = new JsonObject();
			e.params().forEach((k, v) -> params.add(k, primitive(v)));
			eo.add("params", params);
		}
		if (e.generatedBy() != null) eo.addProperty("gen", e.generatedBy());
		return eo;
	}

	public static SceneEvent readEvent(JsonObject eo) {
		Map<String, Object> params = new LinkedHashMap<>();
		if (eo.has("params")) {
			for (Map.Entry<String, JsonElement> p : eo.getAsJsonObject("params").entrySet()) {
				params.put(p.getKey(), readPrimitive(p.getValue().getAsJsonPrimitive()));
			}
		}
		return new SceneEvent(str(eo, "id"), req(eo, "t").getAsInt(), str(eo, "type"),
				eo.has("target") ? str(eo, "target") : null, params,
				eo.has("gen") ? str(eo, "gen") : null);
	}

	public static JsonObject pathPoint(PathPoint pt) {
		JsonObject po = new JsonObject();
		po.add("pos", vec(pt.pos()));
		if (pt.handleIn() != null) po.add("in", vec(pt.handleIn()));
		if (pt.handleOut() != null) po.add("out", vec(pt.handleOut()));
		return po;
	}

	public static PathPoint readPathPoint(JsonObject po) {
		return new PathPoint(readVec(req(po, "pos")),
				po.has("in") ? readVec(po.get("in")) : null,
				po.has("out") ? readVec(po.get("out")) : null);
	}

	public static JsonArray blockPosJson(BlockPos p) {
		return blockPos(p);
	}

	public static BlockPos readBlockPosJson(JsonElement e) {
		return readBlockPos(e);
	}

	private static <T> JsonObject channel(Channel<T> ch) {
		JsonObject o = new JsonObject();
		o.addProperty("type", ch.type().id());
		o.add("default", value(ch.type(), ch.defaultValue()));
		JsonArray keys = new JsonArray();
		for (Keyframe<T> k : ch.keys()) {
			keys.add(keyframe(ch.type(), k));
		}
		o.add("keys", keys);
		return o;
	}

	public static <T> JsonElement value(ValueType<T> type, T value) {
		if (type == ValueType.FLOAT || type == ValueType.INT) {
			return new JsonPrimitive((Number) value);
		} else if (type == ValueType.BOOL) {
			return new JsonPrimitive((Boolean) value);
		} else if (type == ValueType.VEC3) {
			return vec((Vec3) value);
		} else {
			return new JsonPrimitive((String) value);
		}
	}

	private static JsonPrimitive primitive(Object v) {
		if (v instanceof Number n) return new JsonPrimitive(n);
		if (v instanceof Boolean b) return new JsonPrimitive(b);
		return new JsonPrimitive((String) v);
	}

	public static JsonArray vec(Vec3 v) {
		JsonArray a = new JsonArray();
		a.add(v.x());
		a.add(v.y());
		a.add(v.z());
		return a;
	}

	private static JsonArray blockPos(BlockPos p) {
		JsonArray a = new JsonArray();
		a.add(p.x());
		a.add(p.y());
		a.add(p.z());
		return a;
	}

	private static JsonArray strings(List<String> list) {
		JsonArray a = new JsonArray();
		list.forEach(a::add);
		return a;
	}

	// ---- Reading ----

	public static Scene fromJson(JsonObject root) throws SceneFormatException {
		root = SceneMigrations.upgrade(root);
		try {
			return readScene(root);
		} catch (IllegalArgumentException | IllegalStateException | NullPointerException
				| UnsupportedOperationException | ClassCastException e) {
			throw new SceneFormatException("Scene file is damaged: " + e.getMessage(), e);
		}
	}

	private static Scene readScene(JsonObject o) {
		Scene scene = new Scene(str(o, "name"), req(o, "length").getAsInt());
		scene.setOrigin(readBlockPos(req(o, "origin")));
		if (o.has("bounds")) {
			JsonObject b = o.getAsJsonObject("bounds");
			scene.setBounds(new BlockBox(readBlockPos(req(b, "min")), readBlockPos(req(b, "max"))));
		}
		if (o.has("settings")) {
			scene.setSettings(readSettings(o.getAsJsonObject("settings")));
		}
		if (o.has("ids")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("ids").entrySet()) {
				scene.setIdCounter(e.getKey(), e.getValue().getAsInt());
			}
		}
		for (JsonElement p : array(o, "paths")) {
			scene.addPath(readPath(p.getAsJsonObject()));
		}
		for (JsonElement obj : array(o, "objects")) {
			scene.addObject(readObject(obj.getAsJsonObject()));
		}
		return scene;
	}

	public static SceneSettings readSettings(JsonObject o) {
		SceneSettings d = SceneSettings.DEFAULTS;
		GroundFilter filter = d.groundFilter();
		if (o.has("groundFilter")) {
			JsonObject g = o.getAsJsonObject("groundFilter");
			filter = new GroundFilter(
					str(g, "preset"),
					readStrings(array(g, "include")),
					readStrings(array(g, "exclude")),
					FluidMode.byId(str(g, "fluids")),
					req(g, "searchUp").getAsInt(),
					req(g, "searchDown").getAsInt(),
					req(g, "headroomCheck").getAsBoolean());
		}
		InteractionRules rules = o.has("rules")
				? readRules(o.getAsJsonObject("rules")).withFallback(InteractionRules.DEFAULTS)
				: d.rules();
		int range = o.has("trackingRange") ? o.get("trackingRange").getAsInt() : d.trackingRange();
		return new SceneSettings(filter, rules, range);
	}

	public static InteractionRules readRules(JsonObject o) {
		ExplosionRules explosions = ExplosionRules.INHERIT;
		if (o.has("explosions")) {
			JsonObject e = o.getAsJsonObject("explosions");
			explosions = new ExplosionRules(optBool(e, "breakBlocks"), optBool(e, "damageObjects"),
					optBool(e, "damageRealEntities"), optBool(e, "dropItems"), optBool(e, "fire"));
		}
		return new InteractionRules(
				o.has("attack") ? AttackMode.byId(str(o, "attack")) : null,
				o.has("knockback") ? o.get("knockback").getAsDouble() : null,
				o.has("crits") ? CritMode.byId(str(o, "crits")) : null,
				optBool(o, "hitCooldown"),
				optBool(o, "autoDeath"),
				optBool(o, "friendlyFire"),
				explosions);
	}

	public static MotionPath readPath(JsonObject o) {
		MotionPath p = new MotionPath(str(o, "id"), str(o, "name"), PathKind.byId(str(o, "kind")));
		if (o.has("gait")) p.setGait(Gait.byId(str(o, "gait")));
		if (o.has("speed")) {
			JsonObject speed = o.getAsJsonObject("speed");
			p.setBaseSpeed(req(speed, "base").getAsDouble());
			List<SpeedKey> keys = new ArrayList<>();
			for (JsonElement k : array(speed, "keys")) {
				JsonObject ko = k.getAsJsonObject();
				keys.add(new SpeedKey(req(ko, "at").getAsDouble(), req(ko, "value").getAsDouble()));
			}
			p.setSpeedKeys(keys);
		}
		for (JsonElement e : array(o, "points")) {
			p.points().add(readPathPoint(e.getAsJsonObject()));
		}
		List<PathMarker> markers = new ArrayList<>();
		for (JsonElement e : array(o, "markers")) {
			JsonObject mo = e.getAsJsonObject();
			double at = req(mo, "at").getAsDouble();
			markers.add(switch (PathMarker.Kind.byId(str(mo, "type"))) {
				case JUMP -> PathMarker.jump(at);
				case WAIT -> PathMarker.waitFor(at, req(mo, "ticks").getAsInt());
				case GAIT -> PathMarker.gait(at, Gait.byId(str(mo, "gait")));
			});
		}
		p.setMarkers(markers);
		if (o.has("jumpHeight")) p.setJumpHeight(o.get("jumpHeight").getAsDouble());
		return p;
	}

	public static SceneObject readObject(JsonObject o) {
		SceneObject obj = new SceneObject(str(o, "id"), str(o, "name"), str(o, "type"));
		if (o.has("appearance")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("appearance").entrySet()) {
				obj.appearance().put(e.getKey(), e.getValue().getAsString());
			}
		}
		if (o.has("life")) {
			JsonArray life = o.getAsJsonArray("life");
			obj.setLifetime(life.get(0).getAsInt(), life.get(1).getAsInt());
		}
		if (o.has("group")) obj.setGroup(str(o, "group"));
		if (o.has("gen")) obj.setGeneratedBy(str(o, "gen"));
		for (JsonElement e : array(o, "motion")) {
			JsonObject co = e.getAsJsonObject();
			TimingMode timing = TimingMode.byId(str(co, "timing"));
			obj.addMotion(new MotionClip(str(co, "path"), req(co, "start").getAsInt(), timing,
					timing == TimingMode.FIT ? req(co, "end").getAsInt() : -1,
					co.has("offset") ? co.get("offset").getAsDouble() : 0));
		}
		if (o.has("channels")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("channels").entrySet()) {
				obj.putChannel(e.getKey(), readChannel(e.getValue().getAsJsonObject()));
			}
		}
		for (JsonElement e : array(o, "events")) {
			obj.addEvent(readEvent(e.getAsJsonObject()));
		}
		if (o.has("rules")) obj.setRules(readRules(o.getAsJsonObject("rules")));
		return obj;
	}

	private static Channel<?> readChannel(JsonObject o) {
		return readChannel(ValueType.byId(str(o, "type")), o);
	}

	private static <T> Channel<T> readChannel(ValueType<T> type, JsonObject o) {
		Channel<T> ch = new Channel<>(type, readValue(type, req(o, "default")));
		for (JsonElement e : array(o, "keys")) {
			ch.put(readKeyframe(type, e.getAsJsonObject()));
		}
		return ch;
	}

	public static <T> T readValue(ValueType<T> type, JsonElement e) {
		Object v;
		if (type == ValueType.FLOAT) {
			v = e.getAsDouble();
		} else if (type == ValueType.INT) {
			v = e.getAsInt();
		} else if (type == ValueType.BOOL) {
			v = e.getAsBoolean();
		} else if (type == ValueType.VEC3) {
			v = readVec(e);
		} else {
			v = e.getAsString();
		}
		return type.cast(v);
	}

	private static Object readPrimitive(JsonPrimitive p) {
		if (p.isBoolean()) return p.getAsBoolean();
		if (p.isNumber()) return p.getAsDouble();
		return p.getAsString();
	}

	public static Vec3 readVec(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
	}

	private static BlockPos readBlockPos(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
	}

	private static List<String> readStrings(JsonArray a) {
		List<String> out = new ArrayList<>();
		a.forEach(e -> out.add(e.getAsString()));
		return out;
	}

	private static JsonElement req(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			throw new IllegalArgumentException("missing \"" + key + "\"");
		}
		return e;
	}

	private static String str(JsonObject o, String key) {
		return req(o, key).getAsString();
	}

	private static Boolean optBool(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsBoolean() : null;
	}

	private static JsonArray array(JsonObject o, String key) {
		return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
	}
}
