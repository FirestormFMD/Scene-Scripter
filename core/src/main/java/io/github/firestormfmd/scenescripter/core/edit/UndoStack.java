package io.github.firestormfmd.scenescripter.core.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.scene.Scene;

/**
 * Applies edits to a scene and keeps undo and redo history.
 */
public final class UndoStack {
	private final Scene scene;
	private final int limit;
	/** Inverse ops, most recent first. */
	private final Deque<Entry> undo = new ArrayDeque<>();
	/** Ops that redo undone edits, most recent first. */
	private final Deque<Entry> redo = new ArrayDeque<>();
	private long revision;

	public UndoStack(Scene scene, int limit) {
		this.scene = Objects.requireNonNull(scene, "scene");
		if (limit <= 0) {
			throw new IllegalArgumentException("Undo limit must be positive");
		}
		this.limit = limit;
	}

	public Scene scene() {
		return scene;
	}

	/** Applies an edit and records it. Clears the redo history. */
	public void perform(EditOp op) {
		EditOp inverse = op.apply(scene);
		undo.push(new Entry(op.label(), inverse));
		if (undo.size() > limit) {
			undo.removeLast();
		}
		redo.clear();
		revision++;
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	/** Label of the edit {@link #undo()} would reverse, such as "Add Zombie". */
	public Optional<String> undoLabel() {
		return Optional.ofNullable(undo.peek()).map(Entry::label);
	}

	/** Label of the edit {@link #redo()} would reapply. */
	public Optional<String> redoLabel() {
		return Optional.ofNullable(redo.peek()).map(Entry::label);
	}

	public boolean undo() {
		Entry entry = undo.poll();
		if (entry == null) {
			return false;
		}
		redo.push(new Entry(entry.label, entry.op.apply(scene)));
		revision++;
		return true;
	}

	public boolean redo() {
		Entry entry = redo.poll();
		if (entry == null) {
			return false;
		}
		undo.push(new Entry(entry.label, entry.op.apply(scene)));
		revision++;
		return true;
	}

	/** Increases with every change, so callers can tell when the scene needs saving or re-solving. */
	public long revision() {
		return revision;
	}

	/** An op to run for undo or redo, labelled with the edit the user made. */
	private record Entry(String label, EditOp op) {
	}
}
