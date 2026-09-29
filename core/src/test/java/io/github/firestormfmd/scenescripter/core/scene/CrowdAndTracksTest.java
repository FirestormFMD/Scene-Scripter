package io.github.firestormfmd.scenescripter.core.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.crowd.CrowdBuilder;
import io.github.firestormfmd.scenescripter.core.crowd.Formation;
import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.edit.UndoStack;
import io.github.firestormfmd.scenescripter.core.io.SceneCodec;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.runtime.BodyProvider;
import io.github.firestormfmd.scenescripter.core.runtime.EventWindow;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;

class CrowdAndTracksTest {
	@Test
	void gridCrowdsStandApartAndMarchInStep() {
		SceneObject leader = new SceneObject("o1", "Soldier", "minecraft:mannequin");
		leader.channel(BuiltInChannels.POSITION).setDefaultValue(new Vec3(0, 64, 0));
		leader.addMotion(MotionClip.atSpeed("p1", 20));
		leader.addEvent(new SceneEvent("e1", 40, "attack", null, Map.of(), null));
		List<SceneObject> crowd = CrowdBuilder.build(leader, Formation.GRID, 8, 2.0, i -> "c" + i, 5, 0, 1);
		assertEquals(8, crowd.size());
		Set<Vec3> places = new HashSet<>();
		places.add(new Vec3(0, 64, 0));
		for (SceneObject m : crowd) {
			places.add(m.channel(BuiltInChannels.POSITION).defaultValue());
			MotionClip clip = m.motion().getFirst();
			assertTrue(clip.startTick() >= 20, "members start with or after the leader");
			assertEquals(1, m.events().size());
			assertTrue(m.events().getFirst().id().startsWith(m.id() + "_"));
		}
		assertEquals(9, places.size(), "everyone stands in their own place");
		assertTrue(crowd.stream().anyMatch(m -> m.motion().getFirst().lateralOffset() != 0), "ranks walk side by side");
		assertTrue(crowd.stream().anyMatch(m -> m.motion().getFirst().startTick() > 20), "rear ranks start later");
	}

	@Test
	void circlesAndScattersKeepTheirSpacing() {
		for (Formation f : List.of(Formation.CIRCLE, Formation.SCATTER, Formation.LINE)) {
			List<Vec3> offsets = f.offsets(12, 1.5, 7);
			for (int i = 0; i < offsets.size(); i++) {
				for (int j = i + 1; j < offsets.size(); j++) {
					assertTrue(offsets.get(i).distanceTo(offsets.get(j)) >= 1.5 - 1e-9, f + " members " + i + " and " + j);
				}
			}
		}
	}

	@Test
	void sceneTrackKeysTimeAndWeatherAndFiresItsEvents() throws Exception {
		Scene scene = new Scene("s", 400);
		UndoStack history = new UndoStack(scene, 50);
		history.perform(new Edits.SetKeyframe(Scene.TRACKS_ID, BuiltInChannels.TIME_OF_DAY.name(),
				Keyframe.of(0, 1000, Interpolation.LINEAR)));
		history.perform(new Edits.SetKeyframe(Scene.TRACKS_ID, BuiltInChannels.TIME_OF_DAY.name(),
				Keyframe.of(200, 13000, Interpolation.LINEAR)));
		history.perform(new Edits.SetKeyframe(Scene.TRACKS_ID, BuiltInChannels.WEATHER.name(),
				Keyframe.of(100, "thunder", Interpolation.STEP)));
		history.perform(new Edits.AddEvent(Scene.TRACKS_ID, new SceneEvent("s1", 50, "sound", null,
				Map.of("sound", "entity.lightning_bolt.thunder"), null)));
		assertTrue(scene.objects().isEmpty(), "the scene track is not an object");

		SceneEvaluator eval = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED);
		assertEquals(7000, eval.tracks(100).timeOfDay());
		assertEquals("clear", eval.tracks(50).weather(), "before the first weather key the default holds");
		assertEquals("thunder", eval.tracks(150).weather());
		assertEquals(1, EventWindow.between(scene, 40, 60).size());

		Scene back = SceneCodec.fromJson(SceneCodec.toJson(scene));
		assertEquals(7000, new SceneEvaluator(back, null, BodyProvider.PLAYER_SIZED).tracks(100).timeOfDay());
		assertTrue(back.tracks().findEvent("s1").isPresent());

		history.undo();
		assertTrue(scene.tracks().events().isEmpty());
	}

	@Test
	void untouchedTracksLeaveTheWorldAlone() {
		Scene scene = new Scene("s", 100);
		SceneEvaluator.TrackState t = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED).tracks(50);
		assertNull(t.timeOfDay());
		assertNull(t.weather());
	}

	@Test
	void translatingMovesEverythingTogether() {
		Scene scene = new Scene("s", 100);
		scene.setOrigin(new io.github.firestormfmd.scenescripter.core.math.BlockPos(0, 64, 0));
		SceneObject o = new SceneObject("o1", "A", "minecraft:zombie");
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(10, new Vec3(1, 64, 1)));
		o.channel(BuiltInChannels.LOOK_AT).setDefaultValue("5 65 5");
		o.addEvent(new SceneEvent("b", 20, "break_block", null, Map.of("x", 3, "y", 63, "z", 3), null));
		o.addEvent(new SceneEvent("s", 30, "shoot", "o2", Map.of("at", "10 64 10"), null));
		scene.addObject(o);
		MotionPath p = new MotionPath("p1", "P", PathKind.GROUND);
		p.points().add(PathPoint.at(0, 64, 0));
		scene.addPath(p);
		SceneTransform.moveOrigin(scene, new io.github.firestormfmd.scenescripter.core.math.BlockPos(100, 70, -50));
		assertEquals(new Vec3(101, 70, -49), o.channel(BuiltInChannels.POSITION).keys().getFirst().value());
		assertEquals("105 71 -45", o.channel(BuiltInChannels.LOOK_AT).defaultValue());
		assertEquals(103, ((Number) o.findEvent("b").get().params().get("x")).intValue());
		assertEquals("110 70 -40", o.findEvent("s").get().params().get("at"));
		assertEquals(new Vec3(100, 70, -50), p.points().getFirst().pos());
		assertEquals(new io.github.firestormfmd.scenescripter.core.math.BlockPos(100, 70, -50), scene.origin());
	}

	@Test
	void theTutorialSceneSolvesAndSaves() throws Exception {
		Scene scene = TutorialScene.build("tutorial", new io.github.firestormfmd.scenescripter.core.math.BlockPos(0, 64, 0));
		SceneEvaluator eval = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED);
		var solution = new io.github.firestormfmd.scenescripter.core.solve.Solver(
				io.github.firestormfmd.scenescripter.core.solve.CombatModel.DEFAULT).solve(scene, eval,
				io.github.firestormfmd.scenescripter.core.solve.BlockWorld.EMPTY);
		assertEquals(1, solution.explosions().size());
		assertTrue(scene.object("e80:projectile").isPresent(), "the archer's arrow flies");
		Scene back = SceneCodec.fromJson(SceneCodec.toJson(scene));
		assertEquals(scene.objects().size(), back.objects().size());
		assertEquals(2, back.tracks().events().size());
	}

	@Test
	void groupsMoveAndRetimeTogether() {
		SceneObject o = new SceneObject("o1", "A", "minecraft:zombie");
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(10, new Vec3(1, 64, 1)));
		o.addEvent(new SceneEvent("e1", 30, "attack", null, Map.of(), null));
		o.addMotion(MotionClip.fitted("p1", 40, 80));
		o.setLifetime(5, 100);
		SceneTransform.retimeObject(o, 20);
		assertEquals(30, o.channel(BuiltInChannels.POSITION).keys().getFirst().tick());
		assertEquals(50, o.findEvent("e1").get().tick());
		assertEquals(60, o.motion().getFirst().startTick());
		assertEquals(100, o.motion().getFirst().endTick());
		assertEquals(25, o.spawnTick());
		assertEquals(120, o.despawnTick());
		SceneTransform.moveObject(o, new Vec3(2, 0, -3));
		assertEquals(new Vec3(3, 64, -2), o.channel(BuiltInChannels.POSITION).keys().getFirst().value());
	}

	@Test
	void crowdSpeedsVary() {
		SceneObject leader = new SceneObject("o1", "S", "minecraft:zombie");
		leader.addMotion(MotionClip.atSpeed("p1", 0));
		var crowd = io.github.firestormfmd.scenescripter.core.crowd.CrowdBuilder.build(leader,
				io.github.firestormfmd.scenescripter.core.crowd.Formation.LINE, 6, 1.5, i -> "c" + i, 0, 0, 0.1, 3);
		assertTrue(crowd.stream().map(m -> m.motion().getFirst().speedScale()).distinct().count() > 1);
		assertTrue(crowd.stream().allMatch(m -> Math.abs(m.motion().getFirst().speedScale() - 1) <= 0.1 + 1e-9));
	}

	@Test
	void eventConditionsReadVariables() {
		Scene scene = new Scene("s", 100);
		SceneObject o = new SceneObject("o1", "A", "minecraft:zombie");
		var lives = new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
				io.github.firestormfmd.scenescripter.core.anim.ValueType.INT, 3);
		lives.put(Keyframe.of(0, 3, Interpolation.STEP));
		lives.put(Keyframe.of(50, 0, Interpolation.STEP));
		o.putChannel("lives", lives);
		scene.addObject(o);
		scene.tracks().putChannel("alarm", new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
				io.github.firestormfmd.scenescripter.core.anim.ValueType.BOOL, true));
		assertTrue(EventCondition.holds("lives <= 0", o, scene, 60));
		assertTrue(!EventCondition.holds("lives <= 0", o, scene, 40));
		assertTrue(EventCondition.holds("lives > 2", o, scene, 10));
		assertTrue(EventCondition.holds("scene.alarm == true", o, scene, 10));
		assertTrue(!EventCondition.holds("scene.alarm != true", o, scene, 10));
		assertTrue(EventCondition.holds("", o, scene, 10));
		assertTrue(EventCondition.holds("nonsense", o, scene, 10), "unreadable conditions let the event happen");
		assertTrue(EventCondition.holds("health >= 20", o, scene, 10), "built-in channels fall back to their default");
	}

	@Test
	void conditionalAttacksOnlyLandWhenTheyHold() {
		Scene scene = new Scene("s", 200);
		SceneObject a = new SceneObject("a", "A", "minecraft:mannequin");
		a.channel(BuiltInChannels.POSITION).setDefaultValue(new Vec3(0, 64, 0));
		SceneObject b = new SceneObject("b", "B", "minecraft:zombie");
		b.channel(BuiltInChannels.POSITION).setDefaultValue(new Vec3(0, 64, 2));
		scene.addObject(a);
		scene.addObject(b);
		a.addEvent(new SceneEvent("e1", 20, "attack", "b", Map.of("if", "scene.fight == true"), null));
		SceneEvaluator eval = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED);
		var solver = new io.github.firestormfmd.scenescripter.core.solve.Solver(
				io.github.firestormfmd.scenescripter.core.solve.CombatModel.DEFAULT);
		scene.tracks().putChannel("fight", new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
				io.github.firestormfmd.scenescripter.core.anim.ValueType.BOOL, false));
		assertTrue(solver.solve(scene, eval).isEmpty(), "the attack is skipped while the condition fails");
		scene.tracks().putChannel("fight", new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
				io.github.firestormfmd.scenescripter.core.anim.ValueType.BOOL, true));
		assertTrue(solver.solve(scene, eval).getFirst().hit());
	}
}
