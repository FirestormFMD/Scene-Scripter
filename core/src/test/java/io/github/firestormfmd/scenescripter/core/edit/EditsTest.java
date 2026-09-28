package io.github.firestormfmd.scenescripter.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

class EditsTest {
	private final Scene scene = new Scene("test", 200);
	private final UndoStack history = new UndoStack(scene, 100);

	private SceneObject zombie() {
		return new SceneObject("o1", "Zombie", "minecraft:zombie");
	}

	@Test
	void addObjectUndoAndRedo() {
		history.perform(new Edits.AddObject(zombie()));
		assertTrue(scene.object("o1").isPresent());

		history.undo();
		assertTrue(scene.object("o1").isEmpty());
		assertEquals("Add Zombie", history.redoLabel().orElseThrow());

		history.redo();
		assertTrue(scene.object("o1").isPresent());
	}

	@Test
	void deletedObjectReturnsToItsPlace() {
		history.perform(new Edits.AddObject(zombie()));
		history.perform(new Edits.AddObject(new SceneObject("o2", "Skeleton", "minecraft:skeleton")));
		history.perform(new Edits.AddObject(new SceneObject("o3", "Creeper", "minecraft:creeper")));

		history.perform(new Edits.RemoveObject("o2"));
		assertEquals(List.of("o1", "o3"), scene.objectIds());

		history.undo();
		assertEquals(List.of("o1", "o2", "o3"), scene.objectIds());
	}

	@Test
	void setKeyframeUndoRestoresReplacedKey() {
		history.perform(new Edits.AddObject(zombie()));
		history.perform(new Edits.SetKeyframe("o1", "health", Keyframe.of(10, 15.0)));
		history.perform(new Edits.SetKeyframe("o1", "health", Keyframe.of(10, 5.0)));
		SceneObject o = scene.object("o1").orElseThrow();
		assertEquals(5.0, o.channel(BuiltInChannels.HEALTH).valueAt(10));

		history.undo();
		assertEquals(15.0, o.channel(BuiltInChannels.HEALTH).valueAt(10));

		history.undo();
		assertTrue(o.channel(BuiltInChannels.HEALTH).keyAt(10).isEmpty());
	}

	@Test
	void unknownChannelIsRejected() {
		history.perform(new Edits.AddObject(zombie()));
		assertThrows(IllegalArgumentException.class,
				() -> history.perform(new Edits.SetKeyframe("o1", "nonsense", Keyframe.of(0, 1.0))));
		assertEquals("Add Zombie", history.undoLabel().orElseThrow());
	}

	@Test
	void removeEventUndo() {
		history.perform(new Edits.AddObject(zombie()));
		history.perform(new Edits.AddEvent("o1", SceneEvent.of("e1", 40, "attack", "o2")));
		history.perform(new Edits.RemoveEvent("o1", "e1"));
		assertTrue(scene.object("o1").orElseThrow().events().isEmpty());

		history.undo();
		assertEquals("attack", scene.object("o1").orElseThrow().findEvent("e1").orElseThrow().type());
	}

	@Test
	void pathPointEditsUndo() {
		MotionPath path = new MotionPath("p1", "Charge", PathKind.GROUND);
		path.points().add(PathPoint.at(0, 64, 0));
		history.perform(new Edits.AddPath(path));
		history.perform(new Edits.SetPathPoints("p1",
				List.of(PathPoint.at(0, 64, 0), PathPoint.at(10, 64, 0)), "Add point"));
		assertEquals(2, scene.path("p1").orElseThrow().points().size());

		history.undo();
		assertEquals(1, scene.path("p1").orElseThrow().points().size());
	}

	@Test
	void failedCompositeLeavesSceneUnchanged() {
		EditOp bad = new Edits.Composite("Add two", List.of(
				new Edits.AddObject(zombie()),
				new Edits.RemoveObject("does-not-exist")));
		assertThrows(IllegalArgumentException.class, () -> history.perform(bad));
		assertTrue(scene.object("o1").isEmpty());
		assertFalse(history.canUndo());
	}

	@Test
	void compositeUndoesAsOneStep() {
		history.perform(new Edits.Composite("Add two", List.of(
				new Edits.AddObject(zombie()),
				new Edits.SetKeyframe("o1", "dead", Keyframe.of(60, true)))));
		history.undo();
		assertTrue(scene.object("o1").isEmpty());
		assertFalse(history.canUndo());
	}

	@Test
	void undoSnapshotsAreNotChangedByLaterEdits() {
		SceneObject o = zombie();
		history.perform(new Edits.AddObject(o));
		history.perform(new Edits.RenameObject("o1", "Renamed"));
		history.perform(new Edits.RemoveObject("o1"));
		history.undo();
		assertEquals("Renamed", scene.object("o1").orElseThrow().name());
		history.undo();
		assertEquals("Zombie", scene.object("o1").orElseThrow().name());
	}
}
