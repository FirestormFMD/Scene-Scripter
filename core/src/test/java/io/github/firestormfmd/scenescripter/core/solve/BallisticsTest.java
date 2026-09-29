package io.github.firestormfmd.scenescripter.core.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

class BallisticsTest {
	@Test
	void arrowsDropUnderGravity() {
		Vec3 pos = Vec3.ZERO;
		Vec3 vel = Ballistics.velocity(0, 0, 3.0);
		for (int i = 0; i < 10; i++) {
			Vec3[] s = Ballistics.step(Ballistics.ARROW, pos, vel);
			pos = s[0];
			vel = s[1];
		}
		assertTrue(pos.z() > 25 && pos.z() < 30, "flies south about 28 blocks in 10 ticks: " + pos);
		assertTrue(pos.y() < -1.5, "and drops: " + pos);
		assertEquals(0, pos.x(), 1e-9);
	}

	@Test
	void aimedShotsLandOnTheTarget() {
		Vec3 from = new Vec3(0, 65.5, 0);
		for (Vec3 target : new Vec3[] {new Vec3(20, 64.6, 5), new Vec3(-12, 70, -30), new Vec3(3, 60, 2)}) {
			float[] aim = Ballistics.aim(Ballistics.ARROW, from, target, 3.0);
			assertEquals(1, aim[2], "reachable");
			Vec3 d = target.subtract(from);
			Vec3 pos = from;
			Vec3 vel = Ballistics.velocity(aim[0], aim[1], 3.0);
			double best = Double.MAX_VALUE;
			for (int i = 0; i < 100; i++) {
				Vec3[] s = Ballistics.step(Ballistics.ARROW, pos, vel);
				for (int k = 0; k <= 20; k++) {
					best = Math.min(best, Vec3.lerp(pos, s[0], k / 20.0).distanceTo(target));
				}
				pos = s[0];
				vel = s[1];
			}
			assertTrue(best < 0.2, "passes within 0.2 blocks of " + target + " (" + d + "): " + best);
		}
	}

	@Test
	void farTargetsAreOutOfRange() {
		float[] aim = Ballistics.aim(Ballistics.kind("minecraft:snowball"), Vec3.ZERO, new Vec3(0, 0, 300), 1.5);
		assertEquals(0, aim[2]);
	}

	@Test
	void arrowDamageScalesWithSpeed() {
		assertEquals(6, Ballistics.damage(Ballistics.ARROW, new Vec3(0, 0, 3)), 1e-9);
		assertEquals(3, Ballistics.damage(Ballistics.ARROW, new Vec3(0, 0, 1.2)), 1e-9);
		assertEquals(8, Ballistics.damage(Ballistics.kind("minecraft:trident"), new Vec3(0, 0, 1)), 1e-9);
	}

	@Test
	void segmentBoxIntersection() {
		Vec3 min = new Vec3(1, 0, -1);
		Vec3 max = new Vec3(2, 2, 1);
		assertEquals(0.5, Ballistics.segmentHitsBox(new Vec3(0, 1, 0), new Vec3(2, 1, 0), min, max), 1e-9);
		assertEquals(-1, Ballistics.segmentHitsBox(new Vec3(0, 3, 0), new Vec3(2, 3, 0), min, max), 1e-9);
		assertEquals(0, Ballistics.segmentHitsBox(new Vec3(1.5, 1, 0), new Vec3(3, 1, 0), min, max), 1e-9);
	}
}
