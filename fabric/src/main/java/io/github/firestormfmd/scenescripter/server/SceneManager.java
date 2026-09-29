package io.github.firestormfmd.scenescripter.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.core.edit.EditOp;
import io.github.firestormfmd.scenescripter.core.io.EditOpCodec;
import io.github.firestormfmd.scenescripter.core.io.SceneCodec;
import io.github.firestormfmd.scenescripter.core.io.SceneFormatException;
import io.github.firestormfmd.scenescripter.core.journal.BlockJournal;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.net.Chunks;
import io.github.firestormfmd.scenescripter.net.Payloads;

/**
 * The server's scene state: which scene is open, who is editing it, and keeping everyone's view of it up to date.
 * One scene is open at a time.
 */
public final class SceneManager {
	private static final int AUTOSAVE_TICKS = 20 * 60 * 5;
	/** Most blocks per explosion sent to editors for the "will break" preview. */
	private static final int MAX_PREVIEW_BLOCKS = 4096;
	private static SceneManager instance;

	private final MinecraftServer server;
	private final SceneStorage storage;
	private SceneSession session;
	private final Set<UUID> editors = new HashSet<>();
	private final Map<UUID, Chunks.Assembler> incomingEdits = new HashMap<>();
	private final Map<UUID, CaptureSession> captures = new HashMap<>();
	private long sentRevision = -1;
	private int ticksSinceSave;

	private SceneManager(MinecraftServer server) {
		this.server = server;
		this.storage = new SceneStorage(server);
	}

	public static void start(MinecraftServer server) {
		instance = new SceneManager(server);
		instance.recoverJournal();
	}

	public static void stop() {
		if (instance != null) {
			instance.shutdown();
			instance = null;
		}
	}

	public static Optional<SceneManager> get() {
		return Optional.ofNullable(instance);
	}

	public SceneStorage storage() {
		return storage;
	}

	public Optional<SceneSession> session() {
		return Optional.ofNullable(session);
	}

	/** Undoes block changes left behind by a crash or a quit in the middle of a scene. */
	private void recoverJournal() {
		JournalFile.read(storage.journalFile()).ifPresent(leftover -> {
			Identifier dim = Identifier.tryParse(leftover.dimension());
			ServerLevel level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
			if (level == null) {
				SceneScripter.LOGGER.error("Scene journal refers to unknown dimension {}; leaving it in place", leftover.dimension());
				return;
			}
			BlockJournal.restore(leftover.sets(), new WorldBlocks(level));
			new JournalFile(storage.journalFile(), leftover.dimension()).delete();
			SceneScripter.LOGGER.info("Restored {} block change sets left by an unfinished scene", leftover.sets().size());
		});
	}

	private void shutdown() {
		if (session != null) {
			saveQuietly();
			session.close();
			session = null;
		}
	}

	// ---- Scene lifecycle ----

	public Scene create(String rawName, int length, ServerLevel level, net.minecraft.core.BlockPos origin) throws IOException {
		String name = SceneStorage.cleanName(rawName).orElseThrow(() -> new IOException("Scene names can use letters, digits, _ and -"));
		if (storage.exists(name)) {
			throw new IOException("A scene called " + name + " already exists");
		}
		Scene scene = new Scene(name, Math.max(20, length));
		scene.setOrigin(new BlockPos(origin.getX(), origin.getY(), origin.getZ()));
		storage.save(name, scene);
		openScene(name, scene, level);
		return scene;
	}

	public Scene open(String rawName, ServerLevel level) throws IOException, SceneFormatException {
		String name = SceneStorage.cleanName(rawName).orElseThrow(() -> new IOException("Bad scene name"));
		if (!storage.exists(name)) {
			throw new IOException("No scene called " + name);
		}
		Scene scene = storage.load(name);
		openScene(name, scene, level);
		return scene;
	}

	private void openScene(String name, Scene scene, ServerLevel level) {
		close();
		String dimension = level.dimension().identifier().toString();
		session = new SceneSession(name, scene, level, new JournalFile(storage.journalFile(), dimension));
		session.loadTakes(storage.loadTakes(name));
		sentRevision = -1;
		ticksSinceSave = 0;
		broadcastScene();
	}

	public void save() throws IOException {
		if (session == null) {
			throw new IOException("No scene is open");
		}
		storage.save(session.name(), session.scene());
		storage.saveTakes(session.name(), session.takes());
		session.markSaved();
		broadcastState();
	}

	private void saveQuietly() {
		if (session != null && session.isDirty()) {
			try {
				save();
			} catch (IOException e) {
				SceneScripter.LOGGER.error("Could not save scene {}", session.name(), e);
			}
		}
	}

	/** Saves and closes the open scene, removing its actors and undoing its block changes. */
	public void close() {
		if (session == null) {
			return;
		}
		for (CaptureSession c : List.copyOf(captures.values())) {
			endCapture(c, true);
		}
		saveQuietly();
		session.close();
		session = null;
		broadcastScene();
		broadcastActorIds();
	}

	public void delete(String rawName) throws IOException {
		String name = SceneStorage.cleanName(rawName).orElseThrow(() -> new IOException("Bad scene name"));
		if (session != null && session.name().equals(name)) {
			session.close();
			session = null;
			broadcastScene();
		}
		storage.delete(name);
	}

	// ---- Ticking and syncing ----

	public void tick() {
		if (session == null) {
			return;
		}
		session.tick();
		tickCaptures();
		if (session.consumeTakesChanged()) {
			try {
				storage.saveTakes(session.name(), session.takes());
			} catch (IOException e) {
				SceneScripter.LOGGER.error("Could not save takes of scene {}", session.name(), e);
			}
			sentRevision = -1;
		}
		if (session.history().revision() != sentRevision) {
			broadcastScene();
		}
		if (session.consumeStateChanged()) {
			broadcastState();
		}
		if (session.consumeActorListChanged()) {
			broadcastActorIds();
		}
		if (++ticksSinceSave >= AUTOSAVE_TICKS) {
			ticksSinceSave = 0;
			saveQuietly();
		}
	}

	// ---- Requests from editors ----

	public void setEditorOpen(ServerPlayer player, boolean open) {
		if (open) {
			editors.add(player.getUUID());
			sendScene(player);
			sendState(player);
			sendList(player);
		} else {
			editors.remove(player.getUUID());
			incomingEdits.remove(player.getUUID());
		}
	}

	public void onEditPart(ServerPlayer player, Chunks.Part part) {
		incomingEdits.computeIfAbsent(player.getUUID(), u -> new Chunks.Assembler()).accept(part).ifPresent(json -> {
			if (session == null) {
				error(player, "No scene is open");
				return;
			}
			try {
				EditOp op = EditOpCodec.read(json);
				session.perform(op);
			} catch (SceneFormatException | RuntimeException e) {
				error(player, "Edit failed: " + e.getMessage());
				// Resend the scene so the editor drops its optimistic change.
				sendScene(player);
			}
		});
	}

	public void onHistory(ServerPlayer player, boolean redo) {
		if (session != null && !(redo ? session.redo() : session.undo())) {
			error(player, redo ? "Nothing to redo" : "Nothing to undo");
		}
	}

	public void onPlayback(Payloads.Playback p) {
		if (session == null) {
			return;
		}
		switch (p.action()) {
			case Payloads.Playback.PLAY -> session.play();
			case Payloads.Playback.PAUSE -> session.pause();
			case Payloads.Playback.SEEK -> session.seek(p.a());
			case Payloads.Playback.SPEED -> session.setSpeed(p.speed());
			case Payloads.Playback.LOOP -> session.setLoop(p.a(), p.b());
			case Payloads.Playback.STOP -> {
				session.pause();
				session.seek(0);
			}
			default -> {
			}
		}
	}

	public void onSceneCommand(ServerPlayer player, Payloads.SceneCommand c) {
		try {
			switch (c.action()) {
				case Payloads.SceneCommand.NEW -> create(c.name(), c.length(), player.level(), player.blockPosition());
				case Payloads.SceneCommand.OPEN -> open(c.name(), player.level());
				case Payloads.SceneCommand.SAVE -> save();
				case Payloads.SceneCommand.CLOSE -> close();
				case Payloads.SceneCommand.DELETE -> delete(c.name());
				case Payloads.SceneCommand.APPLY -> {
					if (session != null) {
						session.applyToWorld();
						player.sendSystemMessage(Component.literal("The scene's block changes up to tick "
								+ session.clock().tick() + " are now part of the world"));
					}
				}
				case Payloads.SceneCommand.FIT_BOUNDS -> {
					if (session != null) {
						session.fitBounds(8);
					}
				}
				default -> {
				}
			}
		} catch (IOException | SceneFormatException e) {
			error(player, e.getMessage());
		}
		sendList(player);
	}

	// ---- Performance capture ----

	public boolean isCapturing(ServerPlayer player) {
		return captures.containsKey(player.getUUID());
	}

	/**
	 * Starts a player performing an object: the scene rewinds to the pre-roll, the player is placed where the
	 * object is at the punch-in and made invulnerable, and recording starts at the punch-in.
	 *
	 * @param punchOut last tick to record, or -1 for the end of the scene
	 */
	public void startCapture(ServerPlayer player, String objectId, int punchIn, int punchOut, int preroll, boolean loop)
			throws IOException {
		if (session == null) {
			throw new IOException("No scene is open");
		}
		var object = session.scene().object(objectId).orElseThrow(() -> new IOException("No object " + objectId));
		if (isCapturing(player)) {
			throw new IOException("Already capturing; press Right Ctrl to stop first");
		}
		int in = Math.clamp(punchIn, 0, session.scene().length() - 1);
		int out = punchOut < 0 ? -1 : Math.clamp(punchOut, in + 1, session.scene().length());
		CaptureSession c = new CaptureSession(player, object.id(), in, out, Math.max(0, preroll), loop);
		captures.put(player.getUUID(), c);
		player.setInvulnerable(true);
		beginPass(c);
		ServerPlayNetworking.send(player, new Payloads.CaptureState(true, object.id()));
	}

	/** Rewinds to the pre-roll and puts the performer where the object is at the punch-in. */
	private void beginPass(CaptureSession c) {
		session.pause();
		session.setSpeed(1);
		session.seek(c.startTick());
		session.scene().object(c.objectId).ifPresent(o -> {
			var st = session.evaluator().evaluate(o, c.punchIn);
			c.player.connection.teleport(st.position().x(), st.position().y(), st.position().z(), st.bodyYaw(), st.headPitch());
		});
		updateHidden();
		session.play();
	}

	/** Ends a capture, keeping what was recorded so far as a take. */
	public void stopCapture(ServerPlayer player) {
		CaptureSession c = captures.get(player.getUUID());
		if (c != null) {
			endCapture(c, true);
		}
	}

	private void endCapture(CaptureSession c, boolean keep) {
		if (keep && session != null && c.hasSamples()) {
			finishPass(c);
		}
		captures.remove(c.player.getUUID());
		c.release();
		if (session != null) {
			session.pause();
			updateHidden();
		}
		if (ServerPlayNetworking.canSend(c.player, Payloads.CaptureState.TYPE)) {
			ServerPlayNetworking.send(c.player, new Payloads.CaptureState(false, ""));
		}
	}

	private void tickCaptures() {
		for (CaptureSession c : List.copyOf(captures.values())) {
			if (c.player.isRemoved()) {
				endCapture(c, true);
				continue;
			}
			int tick = session.clock().tick();
			c.tick(tick);
			boolean done = c.recording(tick) && ((c.punchOut >= 0 && tick >= c.punchOut) || !session.clock().isPlaying()
					|| tick >= session.scene().length());
			if (!done) {
				continue;
			}
			if (c.loop) {
				if (c.hasSamples()) {
					finishPass(c);
				}
				beginPass(c);
			} else {
				endCapture(c, true);
			}
		}
	}

	/**
	 * Stores the pass as a take and applies it: to the performed object on the first pass, and to a new object on
	 * every later loop pass, so one person can build a crowd.
	 */
	private void finishPass(CaptureSession c) {
		var scene = session.scene();
		var source = scene.object(c.objectId).orElse(null);
		if (source == null) {
			return;
		}
		String takeId = scene.newId("take");
		var take = c.finishPass(takeId, "Take " + (session.takes().size() + 1));
		String target = c.objectId;
		if (c.passes() > 1) {
			var extra = new io.github.firestormfmd.scenescripter.core.scene.SceneObject(scene.newId("o"),
					source.name() + " " + c.passes(), source.entityType());
			extra.appearance().putAll(source.appearance());
			extra.setLifetime(source.spawnTick(), source.despawnTick());
			extra.setGroup(source.group());
			for (var slot : List.of(io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.MAINHAND,
					io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.OFFHAND,
					io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.HEAD,
					io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.CHEST,
					io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.LEGS,
					io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.FEET)) {
				source.channel(slot.name()).ifPresent(ch -> extra.channel(slot).setDefaultValue((String) ch.defaultValue()));
			}
			session.perform(new io.github.firestormfmd.scenescripter.core.edit.Edits.AddObject(extra, scene.objects().size()));
			target = extra.id();
			take = new io.github.firestormfmd.scenescripter.core.capture.Take(take.id(), target, take.name(), take.recordedAt(),
					take.samples(), take.events());
		}
		session.addTake(take);
		session.applyTake(take, target, "keys");
		c.player.sendSystemMessage(Component.literal(take.name() + " recorded for " + scene.object(target).map(
				io.github.firestormfmd.scenescripter.core.scene.SceneObject::name).orElse(target)
				+ " (" + take.samples().size() + " ticks)"));
	}

	private void updateHidden() {
		Set<String> hidden = new HashSet<>();
		for (CaptureSession c : captures.values()) {
			if (c.passes() == 0) {
				hidden.add(c.objectId);
			}
		}
		session.actors().setHidden(hidden);
	}

	/** Records something a performer did, such as a swing, an attack or a block interaction. */
	public void onCaptureEvent(ServerPlayer player, String type, String target, Map<String, Object> params) {
		CaptureSession c = captures.get(player.getUUID());
		if (c != null && session != null) {
			c.event(session.clock().tick(), type, target, params);
		}
	}

	/** Applies a stored take to its object as {@code keys}, {@code raw} keys or a {@code path}. */
	public void useTake(String takeId, String mode) throws IOException {
		if (session == null) {
			throw new IOException("No scene is open");
		}
		var take = session.take(takeId).orElseThrow(() -> new IOException("No take " + takeId));
		session.applyTake(take, take.objectId(), mode);
	}

	public void onCapture(ServerPlayer player, Payloads.Capture c) {
		try {
			switch (c.action()) {
				case Payloads.Capture.START -> startCapture(player, c.objectId(), c.punchIn(), c.punchOut(), c.preroll(), c.loop());
				case Payloads.Capture.STOP -> stopCapture(player);
				case Payloads.Capture.USE_TAKE -> useTake(c.takeId(), c.mode());
				default -> {
				}
			}
		} catch (IOException | RuntimeException e) {
			error(player, e.getMessage() == null ? e.toString() : e.getMessage());
		}
	}

	private static void error(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
	}

	// ---- Sending ----

	private List<ServerPlayer> editorPlayers() {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : editors) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	private String sceneJson() {
		JsonObject o = new JsonObject();
		if (session != null) {
			o.addProperty("name", session.name());
			o.add("scene", SceneCodec.toJson(session.scene()));
			com.google.gson.JsonArray results = new com.google.gson.JsonArray();
			for (var r : session.attackResults()) {
				JsonObject ro = new JsonObject();
				ro.addProperty("event", r.eventId());
				ro.addProperty("attacker", r.attackerId());
				if (r.targetId() != null) {
					ro.addProperty("target", r.targetId());
				}
				ro.addProperty("tick", r.tick());
				ro.addProperty("hit", r.hit());
				ro.addProperty("reason", r.reason());
				ro.addProperty("damage", r.damage());
				results.add(ro);
			}
			o.add("attacks", results);
			com.google.gson.JsonArray blasts = new com.google.gson.JsonArray();
			for (var e : session.explosions()) {
				JsonObject eo = new JsonObject();
				eo.addProperty("event", e.eventId());
				eo.addProperty("owner", e.ownerId());
				eo.addProperty("tick", e.tick());
				eo.add("center", io.github.firestormfmd.scenescripter.core.io.SceneCodec.vec(e.center()));
				eo.addProperty("power", e.power());
				com.google.gson.JsonArray blocks = new com.google.gson.JsonArray();
				int n = 0;
				for (var b : e.blocks()) {
					if (n++ >= MAX_PREVIEW_BLOCKS) {
						break;
					}
					blocks.add(b.x());
					blocks.add(b.y());
					blocks.add(b.z());
				}
				eo.add("blocks", blocks);
				blasts.add(eo);
			}
			o.add("explosions", blasts);
			com.google.gson.JsonArray takes = new com.google.gson.JsonArray();
			for (var t : session.takes()) {
				JsonObject to = new JsonObject();
				to.addProperty("id", t.id());
				to.addProperty("object", t.objectId());
				to.addProperty("name", t.name());
				to.addProperty("start", t.startTick());
				to.addProperty("end", t.endTick());
				takes.add(to);
			}
			o.add("takes", takes);
		}
		return o.toString();
	}

	private void broadcastScene() {
		sentRevision = session == null ? -1 : session.history().revision();
		List<Chunks.Part> parts = Chunks.split(sceneJson());
		for (ServerPlayer p : editorPlayers()) {
			parts.forEach(part -> ServerPlayNetworking.send(p, new Payloads.SceneData(part)));
		}
		broadcastState();
	}

	private void sendScene(ServerPlayer player) {
		Chunks.split(sceneJson()).forEach(part -> ServerPlayNetworking.send(player, new Payloads.SceneData(part)));
	}

	private Payloads.PlaybackState state() {
		if (session == null) {
			return new Payloads.PlaybackState(0, false, 1, -1, -1, false, "", "");
		}
		return new Payloads.PlaybackState(session.clock().tick(), session.clock().isPlaying(),
				(float) session.clock().speed(), session.clock().loopStart(), session.clock().loopEnd(), session.isDirty(),
				session.history().undoLabel().orElse(""), session.history().redoLabel().orElse(""));
	}

	private void broadcastState() {
		Payloads.PlaybackState state = state();
		editorPlayers().forEach(p -> ServerPlayNetworking.send(p, state));
	}

	private void sendState(ServerPlayer player) {
		ServerPlayNetworking.send(player, state());
	}

	private void sendList(ServerPlayer player) {
		ServerPlayNetworking.send(player, new Payloads.SceneList(storage.list()));
	}

	private Payloads.ActorIds actorIds() {
		List<String> objects = new ArrayList<>();
		List<Integer> entities = new ArrayList<>();
		if (session != null) {
			session.actors().entityIds().forEach((o, e) -> {
				objects.add(o);
				entities.add(e);
			});
		}
		return new Payloads.ActorIds(objects, entities);
	}

	/** Every player learns which entities are actors, so their client applies the no-collision rules. */
	private void broadcastActorIds() {
		Payloads.ActorIds ids = actorIds();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(p, Payloads.ActorIds.TYPE)) {
				ServerPlayNetworking.send(p, ids);
			}
		}
	}

	public void onJoin(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Payloads.ActorIds.TYPE)) {
			ServerPlayNetworking.send(player, actorIds());
		}
	}

	public void onLeave(ServerPlayer player) {
		editors.remove(player.getUUID());
		incomingEdits.remove(player.getUUID());
		CaptureSession c = captures.get(player.getUUID());
		if (c != null) {
			endCapture(c, true);
		}
	}

	/** The level new scenes open in when started from the console. */
	public ServerLevel defaultLevel() {
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		return overworld != null ? overworld : server.getAllLevels().iterator().next();
	}
}
