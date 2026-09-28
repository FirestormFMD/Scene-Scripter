package io.github.firestormfmd.scenescripter.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

class BezierSplineTest {
	private static void assertVec(Vec3 expected, Vec3 actual, double eps) {
		assertEquals(expected.x(), actual.x(), eps, "x");
		assertEquals(expected.y(), actual.y(), eps, "y");
		assertEquals(expected.z(), actual.z(), eps, "z");
	}

	@Test
	void uniformControlsMatchClassicCatmullRom() {
		List<Vec3> pts = List.of(new Vec3(0, 0, 0), new Vec3(1, 0, 2), new Vec3(4, 0, 3), new Vec3(6, 0, 0));
		Vec3[] c = BezierSpline.catmullRomControls(pts, 1, 0.0);
		assertVec(pts.get(1).add(pts.get(2).subtract(pts.get(0)).scale(1.0 / 6)), c[0], 1e-9);
		assertVec(pts.get(2).subtract(pts.get(3).subtract(pts.get(1)).scale(1.0 / 6)), c[1], 1e-9);
	}

	@Test
	void passesThroughEveryPoint() {
		List<Vec3> pts = List.of(new Vec3(0, 0, 0), new Vec3(3, 0, 1), new Vec3(5, 0, 6), new Vec3(2, 0, 9));
		BezierSpline spline = BezierSpline.catmullRom(pts, true);
		for (int i = 0; i < spline.segments().size(); i++) {
			assertVec(pts.get(i), spline.segments().get(i).point(0), 1e-12);
			assertVec(pts.get(i + 1), spline.segments().get(i).point(1), 1e-12);
		}
		assertVec(pts.getFirst(), spline.pointAtDistance(0), 1e-9);
		assertVec(pts.getLast(), spline.pointAtDistance(spline.length()), 1e-9);
	}

	@Test
	void evenlySpacedStraightLineIsParameterisedByDistance() {
		List<Vec3> pts = List.of(new Vec3(0, 0, 0), new Vec3(5, 0, 0), new Vec3(10, 0, 0));
		BezierSpline spline = BezierSpline.catmullRom(pts, false);
		assertEquals(10.0, spline.length(), 1e-6);
		assertVec(new Vec3(2.5, 0, 0), spline.pointAtDistance(2.5), 1e-3);
		assertVec(new Vec3(7.25, 0, 0), spline.pointAtDistance(7.25), 1e-3);
		assertVec(new Vec3(1, 0, 0), spline.tangentAtDistance(4), 1e-9);
	}

	@Test
	void horizontalLengthIgnoresHeight() {
		List<Vec3> pts = List.of(new Vec3(0, 0, 0), new Vec3(0, 10, 4));
		assertEquals(4.0, BezierSpline.catmullRom(pts, true).length(), 1e-6);
		assertEquals(Math.sqrt(116), BezierSpline.catmullRom(pts, false).length(), 1e-6);
	}

	@Test
	void duplicatePointsDoNotProduceNaN() {
		List<Vec3> pts = List.of(new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(3, 0, 0));
		BezierSpline spline = BezierSpline.catmullRom(pts, true);
		for (double d = 0; d <= spline.length(); d += 0.25) {
			Vec3 p = spline.pointAtDistance(d);
			assertFalse(Double.isNaN(p.x()) || Double.isNaN(p.z()), "NaN at " + d);
		}
	}

	@Test
	void yawMatchesMinecraftConvention() {
		assertEquals(0f, new Vec3(0, 0, 1).yaw(), 1e-4);
		assertEquals(90f, new Vec3(-1, 0, 0).yaw(), 1e-4);
		assertEquals(-90f, new Vec3(1, 0, 0).yaw(), 1e-4);
	}
}
