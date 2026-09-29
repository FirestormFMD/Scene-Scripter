package io.github.firestormfmd.scenescripter.core.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.runtime.BodyProvider;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

class CaptureTest {
	private static CaptureSample sample(int tick, Vec3 pos, float yaw, boolean onGround) {
		return new CaptureSample(tick, pos, yaw, yaw, 0, onGround, false, false, false, "standing",
				Map.of("mainhand", tick < 130 ? "" : "minecraft:iron_sword"));
	}

	/** Walks east for 40 ticks, then turns and walks north for 40 ticks, starting at tick 100. */
	private static Take cornerTake() {
		List<CaptureSample> s = new ArrayList<>();
		Vec3 p = new Vec3(0, 64, 0);
		for (int i = 0; i <= 80; i++) {
			s.add(sample(100 + i, p, i < 40 ? -90 : 180, true));
			p = p.add(i < 40 ? 0.2 : 0, 0, i < 40 ? 0 : -0.2);
		}
		return new Take("t1", "o1", "Take 1", 0, s, List.of(new SceneEvent("t1:e0", 150, "swing", null, Map.of(), null)));
	}

	@Test
	void simplifyKeepsOnlyCorners() {
		List<Vec3> points = new ArrayList<>();
		for (int i = 0; i <= 20; i++) {
			points.add(new Vec3(i, 0, 0));
		}
		for (int i = 1; i <= 20; i++) {
			points.add(new Vec3(20, 0, i));
		}
		assertEquals(List.of(0, 20, 40), Rdp.simplify(points, 0.01));
	}

	@Test
	void thinnedKeysReproduceThePerformance() {
		Take take = cornerTake();
		Scene scene = new Scene("s", 400);
		SceneObject o = new SceneObject("o1", "Knight", "minecraft:mannequin");
		scene.addObject(o);
		TakeConverter.applyAsKeys(o, take, TakeConverter.KeyMode.THINNED);
		assertTrue(o.channel(BuiltInChannels.POSITION).keys().size() <= 4,
				"a corner needs only a few keys: " + o.channel(BuiltInChannels.POSITION).keys().size());
		SceneEvaluator eval = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED);
		for (CaptureSample s : take.samples()) {
			assertTrue(eval.evaluate(o, s.tick()).position().distanceTo(s.pos()) < TakeConverter.POSITION_TOLERANCE + 1e-9,
					"tick " + s.tick());
		}
		assertEquals("", eval.evaluate(o, 120).equipment().get("mainhand"));
		assertEquals("minecraft:iron_sword", eval.evaluate(o, 140).equipment().get("mainhand"));
		assertTrue(o.findEvent("t1:e0").isPresent());
	}

	@Test
	void rawKeysAreEveryTick() {
		SceneObject o = new SceneObject("o1", "Knight", "minecraft:mannequin");
		TakeConverter.applyAsKeys(o, cornerTake(), TakeConverter.KeyMode.RAW);
		assertEquals(81, o.channel(BuiltInChannels.POSITION).keys().size());
	}

	@Test
	void applyingATakeReplacesOnlyItsRange() {
		SceneObject o = new SceneObject("o1", "Knight", "minecraft:mannequin");
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, new Vec3(5, 64, 5)));
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(150, new Vec3(9, 64, 9)));
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(300, new Vec3(1, 64, 1)));
		o.addEvent(new SceneEvent("old", 120, "attack", null, Map.of(), null));
		o.addEvent(new SceneEvent("later", 250, "attack", null, Map.of(), null));
		TakeConverter.applyAsKeys(o, cornerTake(), TakeConverter.KeyMode.THINNED);
		var keys = o.channel(BuiltInChannels.POSITION).keys();
		assertTrue(keys.stream().anyMatch(k -> k.tick() == 0));
		assertTrue(keys.stream().anyMatch(k -> k.tick() == 300));
		assertTrue(keys.stream().noneMatch(k -> k.tick() == 150 && k.value().equals(new Vec3(9, 64, 9))),
				"keys inside the take's range are replaced");
		assertFalse(o.findEvent("old").isPresent(), "events inside the range are replaced");
		assertTrue(o.findEvent("later").isPresent());
	}

	@Test
	void walkingTakesFitAPathWithWaitsAndJumps() {
		List<CaptureSample> s = new ArrayList<>();
		Vec3 p = new Vec3(0, 64, 0);
		int t = 0;
		for (int i = 0; i < 60; i++, t++) {
			boolean airborne = i >= 20 && i < 30;
			double y = airborne ? 64 + 0.4 * Math.sin((i - 20) / 10.0 * Math.PI) * 3 : 64;
			s.add(sample(t, new Vec3(p.x(), y, p.z()), -90, !airborne));
			p = p.add(0.2158, 0, 0);
		}
		for (int i = 0; i < 20; i++, t++) {
			s.add(sample(t, p, -90, true));
		}
		for (int i = 0; i < 40; i++, t++) {
			s.add(sample(t, p, -90, true));
			p = p.add(0.2158, 0, 0);
		}
		Take take = new Take("t2", "o1", "Walk", 0, s, List.of());
		TakeConverter.PathFit fit = TakeConverter.fitPath(take, "p9", "Captured", 0.3);
		assertNotNull(fit);
		assertTrue(fit.path().points().size() >= 2);
		assertEquals(1, fit.path().markers().stream().filter(m -> m.kind() == PathMarker.Kind.WAIT).count(),
				"one pause: " + fit.path().markers());
		assertEquals(1, fit.path().markers().stream().filter(m -> m.kind() == PathMarker.Kind.JUMP).count(),
				"one jump: " + fit.path().markers());
		assertEquals(4.3, fit.path().baseSpeed(), 0.2, "walking speed in blocks per second");
		assertEquals(0, fit.clip().startTick());
		assertEquals(t - 1, fit.clip().endTick());
	}

	@Test
	void standingTakesHaveNoPath() {
		List<CaptureSample> s = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			s.add(sample(i, new Vec3(0, 64, 0), 0, true));
		}
		assertNull(TakeConverter.fitPath(new Take("t3", "o1", "Idle", 0, s, List.of()), "p", "p", 0.3));
	}

	@Test
	void takesSurviveJson() throws Exception {
		Take take = cornerTake();
		Take back = TakeCodec.fromJson(TakeCodec.toJson(take));
		assertEquals(take.samples().size(), back.samples().size());
		assertTrue(take.samples().get(50).pos().distanceTo(back.samples().get(50).pos()) < 1e-3);
		assertEquals(take.samples().get(50).equipment(), back.samples().get(50).equipment());
		assertEquals(take.samples().get(10).equipment(), back.samples().get(10).equipment());
		assertEquals(take.events(), back.events());
	}
}
