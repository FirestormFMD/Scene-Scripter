package io.github.firestormfmd.scenescripter.core.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;

class ExplosionsTest {
	private static final Vec3 TNT = new Vec3(0.5, 1.06, 0.5);

	@Test
	void nothingBreaksInOpenAir() {
		assertTrue(Explosions.affectedBlocks(new VoxelWorld(), TNT, 4, new Random(1)).isEmpty());
	}

	@Test
	void tntOnDirtLeavesARepeatableCrater() {
		VoxelWorld world = new VoxelWorld().fill(-10, -3, -10, 10, 0, 10, "minecraft:dirt");
		List<BlockPos> a = Explosions.affectedBlocks(world, TNT, 4, new Random(42));
		List<BlockPos> b = Explosions.affectedBlocks(world, TNT, 4, new Random(42));
		assertEquals(a, b, "the same seed always makes the same crater");
		assertTrue(a.size() > 20, "a TNT on dirt digs a real crater, got " + a.size());
		assertTrue(a.contains(new BlockPos(0, 0, 0)), "the block under the TNT goes");
		for (BlockPos p : a) {
			assertTrue(p.y() <= 0 && p.y() >= -3, "only blocks that exist break: " + p);
			assertTrue(Math.abs(p.x()) <= 6 && Math.abs(p.z()) <= 6, "within blast range: " + p);
		}
	}

	@Test
	void obsidianAndStoneResist() {
		VoxelWorld obsidian = new VoxelWorld().fill(-5, -2, -5, 5, 0, 5, "minecraft:obsidian");
		assertTrue(Explosions.affectedBlocks(obsidian, TNT, 4, new Random(3)).isEmpty());
		VoxelWorld stone = new VoxelWorld().fill(-5, -2, -5, 5, 0, 5, "minecraft:stone");
		VoxelWorld dirt = new VoxelWorld().fill(-5, -2, -5, 5, 0, 5, "minecraft:dirt");
		assertTrue(Explosions.affectedBlocks(stone, TNT, 4, new Random(3)).size()
				< Explosions.affectedBlocks(dirt, TNT, 4, new Random(3)).size());
	}

	@Test
	void exposureIsBlockedByWalls() {
		VoxelWorld world = new VoxelWorld();
		Vec3 min = new Vec3(4.7, 0, 0.2);
		Vec3 max = new Vec3(5.3, 1.95, 0.8);
		assertEquals(1.0f, Explosions.seenPercent(world, TNT, min, max), 1e-6);
		world.fill(2, -1, -3, 2, 4, 3, "minecraft:stone");
		assertEquals(0.0f, Explosions.seenPercent(world, TNT, min, max), 1e-6);
	}

	@Test
	void impactFallsOffWithDistance() {
		VoxelWorld world = new VoxelWorld();
		Explosions.Impact near = Explosions.impact(world, TNT, 4, new Vec3(1.5, 1, 0.5), 0.6, 1.95, false, 0);
		Explosions.Impact far = Explosions.impact(world, TNT, 4, new Vec3(6.5, 1, 0.5), 0.6, 1.95, false, 0);
		assertNotNull(near);
		assertNotNull(far);
		assertTrue(near.damage() > far.damage());
		assertTrue(near.damage() > 30, "a TNT a block away is deadly: " + near.damage());
		assertTrue(near.velocity().x() > 0, "pushed away from the blast");
		assertNull(Explosions.impact(world, TNT, 4, new Vec3(9.5, 1, 0.5), 0.6, 1.95, false, 0), "out of range");
	}

	@Test
	void chainFusesAreVanillaRange() {
		Random r = new Random(5);
		for (int i = 0; i < 200; i++) {
			int f = Explosions.chainFuse(r);
			assertTrue(f >= 10 && f < 30, "fuse " + f);
		}
	}

	@Test
	void knockbackResistanceReducesPush() {
		VoxelWorld world = new VoxelWorld();
		Explosions.Impact normal = Explosions.impact(world, TNT, 4, new Vec3(3.5, 1, 0.5), 0.6, 1.95, false, 0);
		Explosions.Impact resisted = Explosions.impact(world, TNT, 4, new Vec3(3.5, 1, 0.5), 0.6, 1.95, false, 1);
		assertEquals(normal.damage(), resisted.damage(), 1e-9);
		assertFalse(resisted.velocity().length() > 1e-9);
	}
}
