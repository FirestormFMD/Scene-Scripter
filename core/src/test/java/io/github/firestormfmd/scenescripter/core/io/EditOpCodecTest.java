package io.github.firestormfmd.scenescripter.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.edit.EditOp;
import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.edit.UndoStack;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

class EditOpCodecTest {
	private static EditOp roundTrip(EditOp op) throws SceneFormatException {
		String json = EditOpCodec.write(op);
		EditOp back = EditOpCodec.read(json);
		assertEquals(json, EditOpCodec.write(back));
		return back;
	}

	@Test
	void everyEditSurvivesTheWire() throws SceneFormatException {
		SceneObject zombie = new SceneObject("o1", "Zombie", "minecraft:zombie");
		MotionPath path = new MotionPath("p1", "Walk", PathKind.GROUND);
		path.points().add(PathPoint.at(1, 64, 1));
		List<EditOp> ops = List.of(
				new Edits.AddObject(zombie, 2),
				new Edits.RemoveObject("o1"),
				new Edits.RenameObject("o1", "Bob"),
				new Edits.ReplaceObject(zombie, "Change lifetime"),
				new Edits.SetKeyframe("o1", "health", Keyframe.of(10, 5.0, Interpolation.BEZIER)),
				new Edits.SetKeyframe("o1", "dead", Keyframe.of(10, true)),
				new Edits.SetKeyframe("o1", "position", Keyframe.of(10, new Vec3(1, 2, 3))),
				new Edits.SetKeyframe("o1", "pose", Keyframe.of(10, "sleeping")),
				new Edits.RemoveKeyframe("o1", "health", 10),
				new Edits.AddEvent("o1", new SceneEvent("e1", 5, "attack", "o2", Map.of("hand", "main"), null)),
				new Edits.RemoveEvent("o1", "e1"),
				new Edits.AddPath(path, -1),
				new Edits.RemovePath("p1"),
				new Edits.ReplacePath(path, "Change speed"),
				new Edits.SetPathPoints("p1", List.of(PathPoint.at(0, 64, 0)), "Move point"),
				Edits.SetSceneHeader.of(new Scene("s", 100)),
				new Edits.NoOp("nothing"),
				new Edits.Composite("both", List.of(new Edits.RemoveObject("o1"), new Edits.RemovePath("p1"))));
		for (EditOp op : ops) {
			roundTrip(op);
		}
	}

	@Test
	void decodedEditsApplyToAScene() throws SceneFormatException {
		Scene scene = new Scene("s", 100);
		UndoStack history = new UndoStack(scene, 10);
		history.perform(roundTrip(new Edits.AddObject(new SceneObject("o1", "Zombie", "minecraft:zombie"))));
		history.perform(roundTrip(new Edits.SetKeyframe("o1", "pose", Keyframe.of(10, "sleeping"))));
		assertEquals("sleeping", scene.object("o1").orElseThrow().channel(BuiltInChannels.POSE).valueAt(20));
	}

	@Test
	void garbageIsRejected() {
		assertThrows(SceneFormatException.class, () -> EditOpCodec.read("{\"op\":\"explode_everything\"}"));
		assertThrows(SceneFormatException.class, () -> EditOpCodec.read("not json"));
	}
}
