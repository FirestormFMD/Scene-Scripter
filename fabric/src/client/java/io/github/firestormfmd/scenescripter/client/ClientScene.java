package io.github.firestormfmd.scenescripter.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.actor.Actors;
import io.github.firestormfmd.scenescripter.core.io.SceneCodec;
import io.github.firestormfmd.scenescripter.core.io.SceneFormatException;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.net.Chunks;
import io.github.firestormfmd.scenescripter.net.Payloads;

/**
 * The client's copy of the open scene, playhead and actor list, kept up to date by the server.
 */
public final class ClientScene {
	private static @Nullable String name;
	private static @Nullable Scene scene;
	private static long version;
	private static Payloads.PlaybackState state = new Payloads.PlaybackState(0, false, 1, -1, -1, false, "", "");
	private static final Map<String, Integer> actorIds = new HashMap<>();
	private static final Map<Integer, String> actorsByEntity = new HashMap<>();
	private static List<String> sceneList = List.of();
	private static List<AttackLine> attacks = List.of();
	private static List<Blast> explosions = List.of();
	private static List<TakeInfo> takes = List.of();
	private static boolean capturing;
	private static io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator evaluator;
	private static long evaluatorVersion = -1;

	/** A recorded take of an object, as listed in the inspector. */
	public record TakeInfo(String id, String objectId, String name, int start, int end) {
	}

	/**
	 * An explosion the scene sets off, for previewing what it will break.
	 *
	 * @param blocks x, y, z of each block it breaks, flattened
	 */
	public record Blast(String eventId, String owner, int tick, io.github.firestormfmd.scenescripter.core.math.Vec3 center,
			float power, int[] blocks) {
	}

	/** How one attack resolved, for drawing hit and miss lines. */
	public record AttackLine(String eventId, String attacker, @Nullable String target, int tick, boolean hit,
			String reason, double damage) {
	}
	private static final Chunks.Assembler ASSEMBLER = new Chunks.Assembler();

	private ClientScene() {
	}

	public static Optional<Scene> scene() {
		return Optional.ofNullable(scene);
	}

	public static @Nullable String name() {
		return name;
	}

	/** Increases whenever a new copy of the scene arrives. */
	public static long version() {
		return version;
	}

	public static Payloads.PlaybackState state() {
		return state;
	}

	public static int tick() {
		return state.tick();
	}

	public static List<AttackLine> attacks() {
		return attacks;
	}

	public static List<Blast> explosions() {
		return explosions;
	}

	public static List<TakeInfo> takes() {
		return takes;
	}

	/** Evaluates the client's copy of the scene, rebuilt whenever a new copy arrives. */
	public static Optional<io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator> evaluator() {
		Minecraft mc = Minecraft.getInstance();
		if (scene == null || mc.level == null) {
			return Optional.empty();
		}
		if (evaluator == null || evaluatorVersion != version || evaluator.scene() != scene) {
			evaluator = new io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator(scene,
					new io.github.firestormfmd.scenescripter.server.LevelTerrain(mc.level, scene.settings().groundFilter()),
					io.github.firestormfmd.scenescripter.server.EntityBodies.INSTANCE);
			evaluatorVersion = version;
		}
		return Optional.of(evaluator);
	}

	/**
	 * While the editor is paused, sets the client-side parts of actors that vanilla animates by itself from the
	 * timeline, so a scrub into the middle of a death shows the body at the right angle.
	 */
	public static void applyScrubOverrides() {
		if (scene == null || state.playing()) {
			return;
		}
		evaluator().ifPresent(eval -> {
			for (SceneObject o : scene.objects()) {
				Entity e = actor(o.id()).orElse(null);
				int tick = state.tick();
				if (e instanceof net.minecraft.world.entity.LivingEntity living) {
					var s = eval.evaluate(o, tick);
					if (s.dead() && s.ticksDead() >= 0) {
						living.deathTime = Math.min(s.ticksDead(), 19);
					}
					int sinceHurt = ticksSince(o, tick, java.util.Set.of("hurt"));
					if (sinceHurt >= 0 && sinceHurt < 10) {
						living.hurtDuration = 10;
						living.hurtTime = 10 - sinceHurt;
					}
					int sinceSwing = ticksSince(o, tick, java.util.Set.of("attack", "swing", "place_block", "break_block", "use_block"));
					if (sinceSwing >= 0 && sinceSwing < 6) {
						living.swinging = true;
						living.swingTime = sinceSwing;
					}
				}
				if (e instanceof net.minecraft.world.entity.monster.Creeper creeper) {
					int swell = o.channel(io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.IGNITED.name())
							.map(ch -> {
								int since = -1;
								for (var k : ch.keys()) {
									if (k.tick() <= tick) {
										since = Boolean.TRUE.equals(k.value()) ? tick - k.tick() : -1;
									}
								}
								return since;
							}).orElse(-1);
					var access = (io.github.firestormfmd.scenescripter.mixin.CreeperAccessor) creeper;
					int value = swell < 0 ? 0 : Math.min(30, swell);
					access.scenescripter$setSwell(value);
					access.scenescripter$setOldSwell(value);
				}
			}
		});
	}

	/** Ticks since the object's last event of these types at or before {@code tick}, or -1. */
	private static int ticksSince(SceneObject o, int tick, java.util.Set<String> types) {
		int best = -1;
		for (var e : o.events()) {
			if (types.contains(e.type()) && e.tick() <= tick) {
				best = tick - e.tick();
			}
		}
		return best;
	}

	/** Whether this player is performing an object right now. */
	public static boolean capturing() {
		return capturing;
	}

	public static void onCaptureState(Payloads.CaptureState s) {
		capturing = s.active();
	}

	public static List<String> sceneList() {
		return sceneList;
	}

	public static Optional<SceneObject> object(@Nullable String id) {
		return id == null || scene == null ? Optional.empty() : scene.object(id);
	}

	public static Optional<Entity> actor(String objectId) {
		Integer id = actorIds.get(objectId);
		Minecraft mc = Minecraft.getInstance();
		if (id == null || mc.level == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(mc.level.getEntity(id));
	}

	public static @Nullable String objectOf(Entity entity) {
		return actorsByEntity.get(entity.getId());
	}

	public static void onSceneData(Chunks.Part part) {
		ASSEMBLER.accept(part).ifPresent(json -> {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			if (!o.has("scene")) {
				name = null;
				scene = null;
				attacks = List.of();
				explosions = List.of();
			} else {
				java.util.List<AttackLine> lines = new java.util.ArrayList<>();
				if (o.has("attacks")) {
					for (var el : o.getAsJsonArray("attacks")) {
						JsonObject a = el.getAsJsonObject();
						lines.add(new AttackLine(a.get("event").getAsString(), a.get("attacker").getAsString(),
								a.has("target") ? a.get("target").getAsString() : null, a.get("tick").getAsInt(),
								a.get("hit").getAsBoolean(), a.get("reason").getAsString(), a.get("damage").getAsDouble()));
					}
				}
				attacks = List.copyOf(lines);
				java.util.List<Blast> blasts = new java.util.ArrayList<>();
				if (o.has("explosions")) {
					for (var el : o.getAsJsonArray("explosions")) {
						JsonObject b = el.getAsJsonObject();
						var arr = b.getAsJsonArray("blocks");
						int[] blocks = new int[arr.size()];
						for (int i = 0; i < blocks.length; i++) {
							blocks[i] = arr.get(i).getAsInt();
						}
						try {
							blasts.add(new Blast(b.get("event").getAsString(), b.get("owner").getAsString(),
									b.get("tick").getAsInt(), SceneCodec.readVec(b.get("center")), b.get("power").getAsFloat(), blocks));
						} catch (RuntimeException e) {
							SceneScripter.LOGGER.warn("Bad explosion preview from the server", e);
						}
					}
				}
				explosions = List.copyOf(blasts);
				java.util.List<TakeInfo> takeList = new java.util.ArrayList<>();
				if (o.has("takes")) {
					for (var el : o.getAsJsonArray("takes")) {
						JsonObject t = el.getAsJsonObject();
						takeList.add(new TakeInfo(t.get("id").getAsString(), t.get("object").getAsString(),
								t.get("name").getAsString(), t.get("start").getAsInt(), t.get("end").getAsInt()));
					}
				}
				takes = List.copyOf(takeList);
				try {
					scene = SceneCodec.fromJson(o.getAsJsonObject("scene"));
					name = o.get("name").getAsString();
				} catch (SceneFormatException e) {
					SceneScripter.LOGGER.error("Could not read the scene sent by the server", e);
				}
			}
			version++;
		});
	}

	public static void onState(Payloads.PlaybackState s) {
		state = s;
	}

	public static void onSceneList(Payloads.SceneList list) {
		sceneList = List.copyOf(list.names());
	}

	/** Marks the listed entities as actors on this client, so collision and targeting skip them. */
	public static void onActorIds(Payloads.ActorIds ids) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			for (Integer old : actorsByEntity.keySet()) {
				Entity e = mc.level.getEntity(old);
				if (e != null) {
					Actors.mark(e, null);
				}
			}
		}
		actorIds.clear();
		actorsByEntity.clear();
		for (int i = 0; i < ids.objectIds().size(); i++) {
			actorIds.put(ids.objectIds().get(i), ids.entityIds().get(i));
			actorsByEntity.put(ids.entityIds().get(i), ids.objectIds().get(i));
		}
		if (mc.level != null) {
			actorsByEntity.forEach((entityId, objectId) -> {
				Entity e = mc.level.getEntity(entityId);
				if (e != null) {
					Actors.mark(e, objectId);
				}
			});
		}
	}

	/** Entities that arrive after the actor list are marked as they load. */
	public static void onEntityLoad(Entity entity) {
		String objectId = actorsByEntity.get(entity.getId());
		if (objectId != null) {
			Actors.mark(entity, objectId);
		}
	}

	public static void reset() {
		capturing = false;
		takes = List.of();
		name = null;
		scene = null;
		version++;
		actorIds.clear();
		actorsByEntity.clear();
		state = new Payloads.PlaybackState(0, false, 1, -1, -1, false, "", "");
	}
}
