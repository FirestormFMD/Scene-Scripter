package io.github.firestormfmd.scenescripter.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.anim.Handles;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.BlockBox;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.AttackMode;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.InteractionRules;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.core.scene.SpeedKey;

class SceneCodecTest {
	private static Scene sampleScene() {
		Scene scene = new Scene("castle_siege", 2400);
		scene.setOrigin(new BlockPos(120, 64, -310));
		scene.setBounds(new BlockBox(new BlockPos(-60, -10, -60), new BlockPos(60, 40, 60)));

		MotionPath path = new MotionPath(scene.newId("p"), "Charge", PathKind.GROUND);
		path.points().add(PathPoint.at(0, 64, 0));
		path.points().add(new PathPoint(new Vec3(12, 64, 4), new Vec3(-2, 0, 0), new Vec3(2, 0, 0)));
		path.setGait(Gait.SPRINT);
		path.setBaseSpeed(Gait.SPRINT.playerSpeed());
		path.setSpeedKeys(List.of(new SpeedKey(0.4, 7.0)));
		path.setMarkers(List.of(PathMarker.jump(0.25), PathMarker.waitFor(0.6, 20), PathMarker.gait(0.8, Gait.WALK)));
		path.setJumpHeight(1.5);
		scene.addPath(path);

		SceneObject knight = new SceneObject(scene.newId("o"), "Knight", "minecraft:mannequin");
		knight.appearance().put("skin", "SomePlayer");
		knight.setLifetime(0, 2000);
		knight.addMotion(MotionClip.atSpeed(path.id(), 40).withLateralOffset(1.5));
		knight.channel(BuiltInChannels.MAINHAND).put(Keyframe.of(0, "minecraft:iron_sword"));
		knight.channel(BuiltInChannels.HEAD_YAW).put(new Keyframe<>(10, 45.0, Interpolation.BEZIER,
				new Handles(-2, 0, 3, 1), null));
		knight.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, new Vec3(1, 2, 3), Interpolation.EASE_IN_OUT));
		knight.addEvent(new SceneEvent(scene.newId("e"), 300, "attack", "o2", Map.of("hand", "main", "power", 1.0), null));
		knight.setRules(InteractionRules.INHERIT.withAttack(AttackMode.ALWAYS_HIT));
		scene.addObject(knight);

		SceneObject zombie = new SceneObject(scene.newId("o"), "Zombie", "minecraft:zombie");
		zombie.addMotion(MotionClip.fitted(path.id(), 0, 200));
		zombie.channel(BuiltInChannels.HEALTH).put(new Keyframe<>(300, 13.0, Interpolation.STEP, null, "e1"));
		zombie.addEvent(new SceneEvent(scene.newId("e"), 300, "hurt", null, Map.of(), "e1"));
		scene.addObject(zombie);
		return scene;
	}

	@Test
	void roundTripKeepsEverything() throws SceneFormatException {
		String first = SceneCodec.write(sampleScene());
		Scene read = SceneCodec.read(first);
		String second = SceneCodec.write(read);
		assertEquals(first, second);

		SceneObject knight = read.object("o1").orElseThrow();
		assertEquals("minecraft:iron_sword", knight.channel(BuiltInChannels.MAINHAND).valueAt(100));
		assertEquals(AttackMode.ALWAYS_HIT, knight.rules().attack());
		assertEquals(1.5, knight.motion().getFirst().lateralOffset());
		assertEquals(3, read.path("p1").orElseThrow().markers().size());
		assertEquals("e1", read.object("o2").orElseThrow().events().getFirst().generatedBy());
	}

	@Test
	void idCountersSurviveSoNewIdsDoNotCollide() throws SceneFormatException {
		Scene read = SceneCodec.read(SceneCodec.write(sampleScene()));
		assertEquals("o3", read.newId("o"));
	}

	@Test
	void newerFormatIsRejectedWithAClearMessage() {
		String json = SceneCodec.write(sampleScene()).replaceFirst("\"format\": 1", "\"format\": 99");
		SceneFormatException e = assertThrows(SceneFormatException.class, () -> SceneCodec.read(json));
		assertTrue(e.getMessage().contains("newer version"), e.getMessage());
	}

	@Test
	void damagedFileReportsTheProblem() {
		String json = "{\"format\": 1, \"length\": 100, \"origin\": [0, 0, 0]}";
		SceneFormatException e = assertThrows(SceneFormatException.class, () -> SceneCodec.read(json));
		assertTrue(e.getMessage().contains("\"name\""), e.getMessage());
	}

	@Test
	void notJson() {
		assertThrows(SceneFormatException.class, () -> SceneCodec.read("{ this is not json"));
	}
}
