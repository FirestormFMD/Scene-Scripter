package io.github.firestormfmd.scenescripter.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.path.VoxelTerrain.Block;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.GroundFilter;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.SpeedKey;

class LocomotionPlannerTest {
	private static final GroundFilter FILTER = GroundFilter.NATURAL_GROUND;

	/** Straight path along +X at z = 0.5, starting on the floor at y = 64. */
	private static MotionPath straight(double fromX, double toX) {
		MotionPath p = new MotionPath("p1", "Test", PathKind.GROUND);
		p.points().add(PathPoint.at(fromX, 64, 0.5));
		p.points().add(PathPoint.at(toX, 64, 0.5));
		return p;
	}

	private static Locomotion plan(MotionPath path, VoxelTerrain terrain) {
		return LocomotionPlanner.plan(path, MotionClip.atSpeed(path.id(), 0), BodySettings.PLAYER, terrain, FILTER);
	}

	@Test
	void walksFlatGroundAtWalkingSpeed() {
		Locomotion loco = plan(straight(0.5, 10.5), VoxelTerrain.floor());
		// 10 blocks at 4.317 blocks per second is about 46.3 ticks.
		assertEquals(47, loco.samples().size() - 1);
		assertTrue(loco.issues().isEmpty());
		for (MotionSample s : loco.samples()) {
			assertEquals(64.0, s.pos().y(), 1e-9);
			assertTrue(s.onGround());
		}
		assertEquals(10.5, loco.samples().getLast().pos().x(), 1e-6);
		assertEquals(-90f, loco.samples().get(10).yaw(), 1e-3); // facing +X (east)
	}

	@Test
	void stepsUpASlabWithoutJumping() {
		VoxelTerrain t = VoxelTerrain.floor().fill(5, 64, -2, 20, 64, 2, Block.SLAB);
		Locomotion loco = plan(straight(0.5, 10.5), t);
		assertTrue(loco.jumpTicks().isEmpty());
		assertTrue(loco.issues().isEmpty());
		assertEquals(64.5, loco.samples().getLast().pos().y(), 1e-9);
	}

	@Test
	void jumpsOntoAFullBlockAndNeverClipsIntoIt() {
		VoxelTerrain t = VoxelTerrain.floor().fill(5, 64, -2, 20, 64, 2, Block.STONE);
		Locomotion loco = plan(straight(0.5, 10.5), t);
		assertEquals(1, loco.jumpTicks().size());
		assertTrue(loco.issues().isEmpty(), () -> "issues: " + loco.issues());
		assertEquals(65.0, loco.samples().getLast().pos().y(), 1e-9);
		// A real arc over the edge, not a hop and a snap.
		double peak = loco.samples().stream().mapToDouble(s -> s.pos().y()).max().orElseThrow();
		assertEquals(64 + 1.2522, peak, 0.01);
		for (MotionSample s : loco.samples()) {
			// Once any part of the hitbox (half width 0.3) is over the block, the body must be on or above it.
			if (s.pos().x() + 0.3 > 5.0 + 1e-6) {
				assertTrue(s.pos().y() >= 65.0 - 1e-9, "clipped into the block at x=" + s.pos().x());
			}
		}
	}

	@Test
	void reportsRisesTooHighToJump() {
		VoxelTerrain t = VoxelTerrain.floor().fill(5, 64, -2, 20, 65, 2, Block.STONE);
		Locomotion loco = plan(straight(0.5, 10.5), t);
		assertTrue(loco.jumpTicks().isEmpty());
		assertEquals(PathIssue.Kind.TOO_HIGH, loco.issues().getFirst().kind());
	}

	@Test
	void higherJumpClearsTwoBlocks() {
		VoxelTerrain t = VoxelTerrain.floor().fill(5, 64, -2, 20, 65, 2, Block.STONE);
		MotionPath path = straight(0.5, 10.5);
		path.setJumpHeight(2.5);
		Locomotion loco = plan(path, t);
		assertEquals(1, loco.jumpTicks().size());
		assertTrue(loco.issues().isEmpty(), () -> "issues: " + loco.issues());
		assertEquals(66.0, loco.samples().getLast().pos().y(), 1e-9);
	}

	@Test
	void aHoleOpeningMidWalkIsFallenInto() {
		VoxelTerrain before = VoxelTerrain.floor();
		VoxelTerrain after = new VoxelTerrain()
				.fill(-5, 63, -2, 4, 63, 2, Block.STONE)
				.fill(9, 63, -2, 30, 63, 2, Block.STONE)
				.fill(5, 59, -2, 8, 59, 2, Block.STONE); // a crater 4 deep from x = 5 to 8
		MotionPath path = straight(0.5, 12.5);
		TerrainTimeline timeline = new TerrainTimeline(tick -> tick < 10 ? before : after, 0);
		Locomotion loco = LocomotionPlanner.plan(path, MotionClip.atSpeed(path.id(), 0), BodySettings.PLAYER, timeline, FILTER);
		double lowest = loco.samples().stream().mapToDouble(s -> s.pos().y()).min().orElseThrow();
		assertEquals(60.0, lowest, 1e-9, "falls to the crater floor");

		Locomotion planned = plan(path, before);
		assertEquals(64.0, planned.samples().stream().mapToDouble(s -> s.pos().y()).min().orElseThrow(), 1e-9,
				"without the blast the walk stays level");
	}

	@Test
	void longDropsAreFlaggedOnlyWhenAsked() {
		VoxelTerrain t = new VoxelTerrain()
				.fill(-5, 66, -2, 4, 66, 2, Block.STONE) // ledge, top at 67
				.fill(-5, 63, -2, 30, 63, 2, Block.STONE); // floor, top at 64
		MotionPath path = straight(0.5, 10.5);
		assertTrue(plan(path, t).issues().isEmpty());
		path.setMaxDrop(2.5);
		assertEquals(PathIssue.Kind.LONG_DROP, plan(path, t).issues().getFirst().kind());
		path.setMaxDrop(3.5);
		assertTrue(plan(path, t).issues().isEmpty());
	}

	@Test
	void fallsOffLedgesWithVanillaGravity() {
		VoxelTerrain t = new VoxelTerrain()
				.fill(-5, 66, -2, 4, 66, 2, Block.STONE) // ledge, top at 67
				.fill(-5, 63, -2, 30, 63, 2, Block.STONE); // floor, top at 64
		Locomotion loco = plan(straight(0.5, 10.5), t);
		assertEquals(1, loco.landings().size());
		assertEquals(3.0, loco.landings().getFirst().fallDistance(), 1e-9);

		int firstAir = -1;
		for (int i = 0; i < loco.samples().size(); i++) {
			if (!loco.samples().get(i).onGround()) {
				firstAir = i;
				break;
			}
		}
		assertTrue(firstAir > 0);
		assertEquals(67.0 - 0.0784, loco.samples().get(firstAir).pos().y(), 1e-9);
		assertEquals(64.0, loco.samples().getLast().pos().y(), 1e-9);
	}

	@Test
	void walksUnderLeavesInsteadOfOntoThem() {
		VoxelTerrain t = VoxelTerrain.floor().fill(3, 66, -2, 8, 67, 2, Block.LEAVES);
		Locomotion loco = plan(straight(0.5, 10.5), t);
		assertTrue(loco.issues().isEmpty());
		loco.samples().forEach(s -> assertEquals(64.0, s.pos().y(), 1e-9));
	}

	@Test
	void waitMarkerHoldsPosition() {
		MotionPath path = straight(0.5, 10.5);
		int plain = plan(path, VoxelTerrain.floor()).samples().size();
		path.setMarkers(List.of(PathMarker.waitFor(0.5, 30)));
		Locomotion loco = plan(path, VoxelTerrain.floor());
		assertEquals(plain + 30, loco.samples().size(), 1);

		long stillAtMiddle = loco.samples().stream().filter(s -> Math.abs(s.pos().x() - 5.5) < 1e-6).count();
		assertTrue(stillAtMiddle >= 30, "waited " + stillAtMiddle + " ticks");
	}

	@Test
	void fittedClipEndsExactlyOnTime() {
		MotionPath path = straight(0.5, 10.5);
		MotionClip clip = MotionClip.fitted(path.id(), 100, 140);
		Locomotion loco = LocomotionPlanner.plan(path, clip, BodySettings.PLAYER, VoxelTerrain.floor(), FILTER);
		assertEquals(100, loco.startTick());
		assertEquals(140, loco.endTick());
		assertEquals(10.5, loco.sampleAt(140).pos().x(), 1e-6);
		assertEquals(5.5, loco.sampleAt(120).pos().x(), 0.05);
	}

	@Test
	void fittedClipTooShortForItsWaitsIsReported() {
		MotionPath path = straight(0.5, 10.5);
		path.setMarkers(List.of(PathMarker.waitFor(0.5, 60)));
		MotionClip clip = MotionClip.fitted(path.id(), 0, 40);
		Locomotion loco = LocomotionPlanner.plan(path, clip, BodySettings.PLAYER, VoxelTerrain.floor(), FILTER);
		assertEquals(PathIssue.Kind.NOT_ENOUGH_TIME, loco.issues().getFirst().kind());
	}

	@Test
	void speedKeysChangeDuration() {
		MotionPath path = straight(0.5, 20.5);
		int walking = plan(path, VoxelTerrain.floor()).samples().size();
		path.setSpeedKeys(List.of(new SpeedKey(0.0, Gait.SPRINT.playerSpeed())));
		int sprinting = plan(path, VoxelTerrain.floor()).samples().size();
		assertTrue(sprinting < walking);
		assertEquals(20 / Gait.SPRINT.playerSpeed() * 20, sprinting - 1, 1);
	}

	@Test
	void lateralOffsetRunsParallelOnTheRight() {
		MotionPath path = straight(0.5, 10.5);
		MotionClip clip = MotionClip.atSpeed(path.id(), 0).withLateralOffset(1.0);
		Locomotion loco = LocomotionPlanner.plan(path, clip, BodySettings.PLAYER, VoxelTerrain.floor(), FILTER);
		// Travelling east (+X), the right-hand side is south (+Z).
		loco.samples().forEach(s -> assertEquals(1.5, s.pos().z(), 1e-6));
	}

	@Test
	void jumpMarkerJumpsOnFlatGround() {
		MotionPath path = straight(0.5, 10.5);
		path.setMarkers(List.of(PathMarker.jump(0.5)));
		Locomotion loco = plan(path, VoxelTerrain.floor());
		assertEquals(1, loco.jumpTicks().size());
		double peak = loco.samples().stream().mapToDouble(s -> s.pos().y()).max().orElseThrow();
		assertEquals(64 + 1.2522, peak, 0.01);
		assertEquals(64.0, loco.samples().getLast().pos().y(), 1e-9);
	}

	@Test
	void gaitMarkerSwitchesGait() {
		MotionPath path = straight(0.5, 10.5);
		path.setMarkers(List.of(PathMarker.gait(0.5, Gait.SNEAK)));
		Locomotion loco = plan(path, VoxelTerrain.floor());
		assertEquals(Gait.WALK, loco.samples().get(5).gait());
		assertEquals(Gait.SNEAK, loco.samples().getLast().gait());
	}

	@Test
	void missingGroundIsReported() {
		VoxelTerrain t = new VoxelTerrain().fill(-5, 63, -2, 4, 63, 2, Block.STONE);
		Locomotion loco = plan(straight(0.5, 10.5), t);
		assertEquals(PathIssue.Kind.NO_GROUND, loco.issues().getFirst().kind());
		assertEquals(1, loco.issues().size());
	}

	@Test
	void swimsAcrossWater() {
		VoxelTerrain t = VoxelTerrain.floor()
				.fill(4, 61, -3, 7, 63, 3, Block.WATER)
				.fill(4, 60, -3, 7, 60, 3, Block.STONE);
		Locomotion loco = plan(straight(0.5, 10.5), t);
		assertTrue(loco.samples().stream().anyMatch(MotionSample::swimming));
		assertFalse(loco.hasIssues(), () -> "issues: " + loco.issues());
	}

	@Test
	void bodyTurnsNoFasterThanItsTurnRate() {
		MotionPath path = new MotionPath("p1", "Corner", PathKind.GROUND);
		path.points().add(PathPoint.at(0.5, 64, 0.5));
		path.points().add(PathPoint.at(8.5, 64, 0.5));
		path.points().add(PathPoint.at(8.5, 64, 8.5));
		Locomotion loco = plan(path, VoxelTerrain.floor());
		for (int i = 1; i < loco.samples().size(); i++) {
			double turn = LocomotionPlanner.wrapDegrees(loco.samples().get(i).yaw() - loco.samples().get(i - 1).yaw());
			assertTrue(Math.abs(turn) <= BodySettings.PLAYER.turnRate() + 1e-3);
		}
		assertEquals(0f, loco.samples().getLast().yaw(), 1.0); // ends facing +Z (south)
	}

	@Test
	void airPathsFollowTheCurveInThreeDimensions() {
		MotionPath path = new MotionPath("p1", "Flight", PathKind.AIR);
		path.points().add(PathPoint.at(0, 80, 0));
		path.points().add(PathPoint.at(10, 90, 0));
		Locomotion loco = LocomotionPlanner.plan(path, MotionClip.atSpeed(path.id(), 0), BodySettings.PLAYER, null, FILTER);
		assertEquals(90.0, loco.samples().getLast().pos().y(), 1e-6);
		assertFalse(loco.samples().getLast().onGround());
	}

	@Test
	void clipEndingMidAirLandsInsteadOfHanging() {
		MotionPath path = straight(0.5, 5.5);
		path.setMarkers(List.of(PathMarker.jump(1.0)));
		Locomotion loco = plan(path, VoxelTerrain.floor());
		assertEquals(1, loco.jumpTicks().size());
		assertTrue(loco.samples().getLast().onGround());
		assertEquals(64.0, loco.samples().getLast().pos().y(), 1e-9);
		assertEquals(5.5, loco.samples().getLast().pos().x(), 1e-6);
	}

	@Test
	void planningIsDeterministic() {
		VoxelTerrain t = VoxelTerrain.floor().fill(5, 64, -2, 20, 64, 2, Block.STONE);
		assertEquals(plan(straight(0.5, 10.5), t), plan(straight(0.5, 10.5), t));
	}
}
