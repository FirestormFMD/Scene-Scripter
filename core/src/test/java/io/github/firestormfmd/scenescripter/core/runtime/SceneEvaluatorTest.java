package io.github.firestormfmd.scenescripter.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

class SceneEvaluatorTest {
	private final Scene scene = new Scene("test", 400);
	private final SceneEvaluator eval = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED);

	private SceneObject zombie() {
		SceneObject o = new SceneObject("o1", "Zombie", "minecraft:zombie");
		scene.addObject(o);
		return o;
	}

	/** Flying path along +X from (0, 70, 0) to (10, 70, 0); air paths need no terrain. */
	private MotionPath airPath() {
		MotionPath p = new MotionPath("p1", "Flight", PathKind.AIR);
		p.points().add(PathPoint.at(0, 70, 0));
		p.points().add(PathPoint.at(10, 70, 0));
		scene.addPath(p);
		return p;
	}

	@Test
	void positionKeysInterpolate() {
		SceneObject o = zombie();
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, new Vec3(0, 64, 0)));
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(20, new Vec3(10, 64, 0)));
		assertEquals(new Vec3(5, 64, 0), eval.evaluate(o, 10).position());
	}

	@Test
	void clipDrivesPositionWhileActiveThenHolds() {
		SceneObject o = zombie();
		airPath();
		o.addMotion(MotionClip.fitted("p1", 20, 60));
		assertEquals(new Vec3(0, 70, 0), eval.evaluate(o, 0).position()); // before: at the path start
		assertEquals(5.0, eval.evaluate(o, 40).position().x(), 0.05);
		assertTrue(eval.evaluate(o, 40).moving());
		assertEquals(10.0, eval.evaluate(o, 100).position().x(), 1e-6); // after: holds the end
		assertFalse(eval.evaluate(o, 100).moving());
	}

	@Test
	void objectMovesFromClipEndToTheNextKey() {
		SceneObject o = zombie();
		airPath();
		o.addMotion(MotionClip.fitted("p1", 0, 40));
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(60, new Vec3(10, 70, 20)));
		assertEquals(new Vec3(10, 70, 10), eval.evaluate(o, 50).position());
	}

	@Test
	void stepKeyHoldsUntilTheClip() {
		SceneObject o = zombie();
		airPath();
		o.addMotion(MotionClip.fitted("p1", 40, 80));
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, new Vec3(-5, 70, 0), Interpolation.STEP));
		assertEquals(new Vec3(-5, 70, 0), eval.evaluate(o, 30).position());
	}

	@Test
	void headIsKeyedRelativeToTheBody() {
		SceneObject o = zombie();
		o.channel(BuiltInChannels.BODY_YAW).put(Keyframe.of(0, 90.0));
		o.channel(BuiltInChannels.HEAD_YAW).put(Keyframe.of(0, 30.0));
		ObjectState s = eval.evaluate(o, 5);
		assertEquals(90f, s.bodyYaw(), 1e-4);
		assertEquals(120f, s.headYaw(), 1e-4);
	}

	@Test
	void lookAtTurnsTheHeadTowardsAnotherObject() {
		SceneObject a = zombie();
		a.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, new Vec3(0, 64, 0)));
		SceneObject b = new SceneObject("o2", "Target", "minecraft:pig");
		b.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, new Vec3(0, 64, 10)));
		scene.addObject(b);
		a.channel(BuiltInChannels.LOOK_AT).put(Keyframe.of(0, "o2"));
		ObjectState s = eval.evaluate(a, 0);
		assertEquals(0f, s.headYaw(), 1e-3); // +Z is yaw 0
	}

	@Test
	void headTurnIsLimited() {
		SceneObject a = zombie();
		a.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, new Vec3(0, 64, 0)));
		a.channel(BuiltInChannels.LOOK_AT).put(Keyframe.of(0, "0 65.53 -10")); // straight behind (yaw 180)
		assertEquals(75f, Math.abs(eval.evaluate(a, 0).headYaw()), 1e-3);
	}

	@Test
	void deathTimerCountsFromWhenDeadWasKeyed() {
		SceneObject o = zombie();
		o.channel(BuiltInChannels.DEAD).put(Keyframe.of(100, true));
		assertFalse(eval.evaluate(o, 50).dead());
		assertEquals(-1, eval.evaluate(o, 50).ticksDead());
		assertEquals(15, eval.evaluate(o, 115).ticksDead());
		o.channel(BuiltInChannels.DEAD).put(Keyframe.of(200, false));
		assertFalse(eval.evaluate(o, 210).dead());
	}

	@Test
	void pathGaitSetsFlags() {
		SceneObject o = zombie();
		MotionPath p = airPath();
		p.setGait(Gait.SPRINT);
		o.addMotion(MotionClip.atSpeed("p1", 0));
		assertTrue(eval.evaluate(o, 5).sprinting());
		assertFalse(eval.evaluate(o, 500).sprinting());
	}

	@Test
	void equipmentAndCustomChannels() {
		SceneObject o = zombie();
		o.channel(BuiltInChannels.MAINHAND).put(Keyframe.of(50, "minecraft:iron_sword"));
		assertEquals("", eval.evaluate(o, 10).equipment().get("mainhand"));
		assertEquals("minecraft:iron_sword", eval.evaluate(o, 60).equipment().get("mainhand"));

		o.putChannel("lives", new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
				io.github.firestormfmd.scenescripter.core.anim.ValueType.INT, 3));
		assertEquals(3, eval.evaluate(o, 0).extra().get("lives"));
	}

	@Test
	void existsFollowsLifetime() {
		SceneObject o = zombie();
		o.setLifetime(20, 80);
		assertFalse(eval.evaluate(o, 10).exists());
		assertTrue(eval.evaluate(o, 20).exists());
		assertFalse(eval.evaluate(o, 80).exists());
	}
}
