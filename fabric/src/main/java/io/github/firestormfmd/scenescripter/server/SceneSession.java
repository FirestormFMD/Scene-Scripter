package io.github.firestormfmd.scenescripter.server;

import java.util.List;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import io.github.firestormfmd.scenescripter.actor.Actors;
import io.github.firestormfmd.scenescripter.core.edit.EditOp;
import io.github.firestormfmd.scenescripter.core.edit.UndoStack;
import io.github.firestormfmd.scenescripter.core.journal.BlockJournal;
import io.github.firestormfmd.scenescripter.core.journal.ChangeSet;
import io.github.firestormfmd.scenescripter.core.math.BlockBox;
import io.github.firestormfmd.scenescripter.core.runtime.EventWindow;
import io.github.firestormfmd.scenescripter.core.runtime.PlaybackClock;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.solve.Solver;

/**
 * An open scene on the server: its data, undo history, playhead, block journal and actors.
 */
public final class SceneSession {
	private final String name;
	private final Scene scene;
	private final UndoStack history;
	private final SceneEvaluator evaluator;
	private final PlaybackClock clock;
	private final BlockJournal<SavedBlock> journal;
	private final WorldBlocks blocks;
	private final ActorController actors;
	private final EventPlayer events;
	private final Solver solver;
	private List<Solver.AttackResult> attackResults = List.of();
	private List<Solver.ExplosionResult> explosions = List.of();
	private List<ChangeSet<SavedBlock>> changeSets = List.of();
	/** Keeps the scene's chunks loaded and ticking without saving the ticket with the world. */
	private static final TicketType SCENE_TICKET = new TicketType(0L,
			TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
	private final List<ChunkPos> loadedChunks = new java.util.ArrayList<>();
	private long appliedRevision = -1;
	private long savedRevision;
	private boolean actorListChanged = true;
	private boolean stateChanged = true;

	public SceneSession(String name, Scene scene, ServerLevel level, JournalFile journalFile) {
		this.name = name;
		this.scene = scene;
		this.history = new UndoStack(scene, 500);
		this.evaluator = new SceneEvaluator(scene, new LevelTerrain(level, scene.settings().groundFilter()),
				EntityBodies.INSTANCE);
		this.clock = new PlaybackClock(scene.length());
		this.journal = new BlockJournal<>(journalFile);
		this.blocks = new WorldBlocks(level);
		this.actors = new ActorController(level);
		this.events = new EventPlayer(this);
		this.solver = new Solver(new WorldCombat(level, false));
		this.savedRevision = history.revision();
		Actors.setTrackingRange(scene.settings().trackingRange());
	}

	public String name() {
		return name;
	}

	public Scene scene() {
		return scene;
	}

	public UndoStack history() {
		return history;
	}

	public SceneEvaluator evaluator() {
		return evaluator;
	}

	public PlaybackClock clock() {
		return clock;
	}

	public ActorController actors() {
		return actors;
	}

	public ServerLevel level() {
		return actors.level();
	}

	public BlockJournal<SavedBlock> journal() {
		return journal;
	}

	public WorldBlocks blocks() {
		return blocks;
	}

	/** Hit or miss of every attack, for the editor to draw. */
	public List<Solver.AttackResult> attackResults() {
		return attackResults;
	}

	/** Every explosion the scene sets off, with the blocks it breaks. */
	public List<Solver.ExplosionResult> explosions() {
		return explosions;
	}

	public java.util.Optional<Solver.ExplosionResult> explosion(String eventId) {
		return explosions.stream().filter(e -> e.eventId().equals(eventId)).findFirst();
	}

	public boolean isDirty() {
		return history.revision() != savedRevision;
	}

	public void markSaved() {
		savedRevision = history.revision();
	}

	/** Whether the actor list or playback state changed since the last call; used to decide what to send. */
	public boolean consumeActorListChanged() {
		boolean c = actorListChanged;
		actorListChanged = false;
		return c;
	}

	public boolean consumeStateChanged() {
		boolean c = stateChanged;
		stateChanged = false;
		return c;
	}

	/** One server tick: advance the playhead if playing, and refresh after edits. */
	public void tick() {
		Optional<PlaybackClock.Step> step = clock.advance();
		if (history.revision() != appliedRevision) {
			refreshAfterEdit();
		}
		if (step.isPresent()) {
			applyStep(step.get());
			stateChanged = true;
		}
	}

	private void refreshAfterEdit() {
		appliedRevision = history.revision();
		VirtualWorld world = new VirtualWorld(level(), journal.originals());
		evaluator.setTerrain(new LevelTerrain(level(), scene.settings().groundFilter(), world::baseState));
		Solver.Solution solution = solver.solve(scene, evaluator, world);
		attackResults = solution.attacks();
		explosions = solution.explosions();
		changeSets = world.changeSets();
		clock.setLength(scene.length());
		Actors.setTrackingRange(scene.settings().trackingRange());
		updateChunkTickets();
		journal.setChangeSets(changeSets, blocks);
		actorListChanged |= actors.update(evaluator, clock.tick(), true);
	}

	/** Loads the chunks inside the scene bounds for as long as the scene is open. */
	private void updateChunkTickets() {
		List<ChunkPos> wanted = new java.util.ArrayList<>();
		BlockBox bounds = scene.bounds();
		if (bounds != null) {
			for (int cx = bounds.min().x() >> 4; cx <= bounds.max().x() >> 4; cx++) {
				for (int cz = bounds.min().z() >> 4; cz <= bounds.max().z() >> 4; cz++) {
					wanted.add(new ChunkPos(cx, cz));
					if (wanted.size() > 1024) {
						break;
					}
				}
			}
		}
		if (wanted.equals(loadedChunks)) {
			return;
		}
		releaseChunks();
		for (ChunkPos pos : wanted) {
			level().getChunkSource().addTicketWithRadius(SCENE_TICKET, pos, 0);
		}
		loadedChunks.addAll(wanted);
	}

	private void releaseChunks() {
		for (ChunkPos pos : loadedChunks) {
			level().getChunkSource().removeTicketWithRadius(SCENE_TICKET, pos, 0);
		}
		loadedChunks.clear();
	}

	/**
	 * Sets the scene bounds to a box around everything the scene uses: every object's positions over the whole
	 * timeline, every path point and every crater, plus a margin.
	 */
	public void fitBounds(int margin) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		List<io.github.firestormfmd.scenescripter.core.math.Vec3> points = new java.util.ArrayList<>();
		for (var o : scene.objects()) {
			for (int t = 0; t <= scene.length(); t += 10) {
				var st = evaluator.evaluate(o, t);
				if (st.exists()) {
					points.add(st.position());
				}
			}
		}
		for (var p : scene.paths()) {
			p.points().forEach(pt -> points.add(pt.pos()));
		}
		for (var e : explosions) {
			points.add(e.center().add(-e.power() * 2, -e.power() * 2, -e.power() * 2));
			points.add(e.center().add(e.power() * 2, e.power() * 2, e.power() * 2));
		}
		if (points.isEmpty()) {
			return;
		}
		for (var v : points) {
			minX = Math.min(minX, (int) Math.floor(v.x()));
			minY = Math.min(minY, (int) Math.floor(v.y()));
			minZ = Math.min(minZ, (int) Math.floor(v.z()));
			maxX = Math.max(maxX, (int) Math.floor(v.x()));
			maxY = Math.max(maxY, (int) Math.floor(v.y()));
			maxZ = Math.max(maxZ, (int) Math.floor(v.z()));
		}
		setBounds(new BlockBox(new io.github.firestormfmd.scenescripter.core.math.BlockPos(minX - margin, minY - margin, minZ - margin),
				new io.github.firestormfmd.scenescripter.core.math.BlockPos(maxX + margin, maxY + margin, maxZ + margin)));
	}

	/** Sets or clears (null) the stage box whose chunks stay loaded while the scene is open. */
	public void setBounds(BlockBox bounds) {
		var h = io.github.firestormfmd.scenescripter.core.edit.Edits.SetSceneHeader.of(scene);
		perform(new io.github.firestormfmd.scenescripter.core.edit.Edits.SetSceneHeader(h.name(), h.length(), h.origin(),
				bounds, h.settings()));
	}

	/**
	 * Makes the scene's block changes up to the playhead permanent. They stay in the world and become part of the
	 * scene's starting state.
	 */
	public void applyToWorld() {
		journal.commit();
		appliedRevision = -1;
		stateChanged = true;
	}

	private void applyStep(PlaybackClock.Step step) {
		journal.seek(step.to(), blocks);
		actorListChanged |= actors.update(evaluator, step.to(), !step.contiguous());
		if (step.contiguous()) {
			for (EventWindow.Fired fired : EventWindow.between(scene, step.from(), step.to())) {
				events.fire(fired.owner(), fired.event());
			}
		}
	}

	/** Block changes the scene makes, from explosions and block events. */
	public List<ChangeSet<SavedBlock>> changeSets() {
		return changeSets;
	}

	public void perform(EditOp op) {
		history.perform(op);
		stateChanged = true;
	}

	public boolean undo() {
		stateChanged = true;
		return history.undo();
	}

	public boolean redo() {
		stateChanged = true;
		return history.redo();
	}

	public void play() {
		clock.play();
		stateChanged = true;
	}

	public void pause() {
		clock.pause();
		stateChanged = true;
	}

	public void seek(int tick) {
		applyStep(clock.seek(tick));
		stateChanged = true;
	}

	public void setSpeed(double speed) {
		clock.setSpeed(speed);
		stateChanged = true;
	}

	public void setLoop(int start, int end) {
		clock.setLoop(start, end);
		stateChanged = true;
	}

	/** Removes actors and undoes every block change, leaving the world as it was before the scene. */
	public void close() {
		clock.pause();
		actors.removeAll();
		journal.revertAll(blocks);
		releaseChunks();
	}
}
