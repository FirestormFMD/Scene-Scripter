package io.github.firestormfmd.scenescripter.server;

import java.util.List;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;

import io.github.firestormfmd.scenescripter.actor.Actors;
import io.github.firestormfmd.scenescripter.core.edit.EditOp;
import io.github.firestormfmd.scenescripter.core.edit.UndoStack;
import io.github.firestormfmd.scenescripter.core.journal.BlockJournal;
import io.github.firestormfmd.scenescripter.core.journal.ChangeSet;
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
		this.solver = new Solver(new WorldCombat(level));
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
		evaluator.setTerrain(new LevelTerrain(level(), scene.settings().groundFilter()));
		attackResults = solver.solve(scene, evaluator);
		clock.setLength(scene.length());
		Actors.setTrackingRange(scene.settings().trackingRange());
		journal.setChangeSets(bakedBlockChanges(), blocks);
		actorListChanged |= actors.update(evaluator, clock.tick(), true);
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
	private List<ChangeSet<SavedBlock>> bakedBlockChanges() {
		return events.blockChanges();
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
	}
}
