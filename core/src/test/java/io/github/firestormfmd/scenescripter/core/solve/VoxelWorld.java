package io.github.firestormfmd.scenescripter.core.solve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;

/** A {@link BlockWorld} of full cubes named by block ID, recording every change. */
final class VoxelWorld implements BlockWorld {
	record Change(String cause, int tick, BlockPos pos, String block) {
	}

	private static final Map<String, Float> RESISTANCE = Map.of(
			"minecraft:stone", 6f, "minecraft:dirt", 0.5f, "minecraft:obsidian", 1200f, "minecraft:tnt", 0f,
			"minecraft:oak_door", 3f, "minecraft:water", 100f);

	private final Map<BlockPos, String> base = new HashMap<>();
	private final Map<BlockPos, String> blocks = new HashMap<>();
	final List<Change> changes = new ArrayList<>();

	VoxelWorld set(int x, int y, int z, String block) {
		base.put(new BlockPos(x, y, z), block);
		blocks.put(new BlockPos(x, y, z), block);
		return this;
	}

	VoxelWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					set(x, y, z, block);
				}
			}
		}
		return this;
	}

	String get(int x, int y, int z) {
		return blocks.getOrDefault(new BlockPos(x, y, z), "minecraft:air");
	}

	@Override
	public float explosionResistance(BlockPos pos) {
		String b = blocks.get(pos);
		return b == null || b.equals("minecraft:air") ? -1 : RESISTANCE.getOrDefault(b, 1f);
	}

	@Override
	public boolean inBuildHeight(int y) {
		return y >= -64 && y < 320;
	}

	@Override
	public boolean isTnt(BlockPos pos) {
		return "minecraft:tnt".equals(blocks.get(pos));
	}

	@Override
	public boolean canLightFire(BlockPos pos) {
		return explosionResistance(pos) < 0 && explosionResistance(pos.offset(0, -1, 0)) >= 0;
	}

	@Override
	public Optional<Vec3> clip(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		int steps = (int) Math.ceil(len / 0.02);
		for (int i = 0; i <= steps; i++) {
			Vec3 p = steps == 0 ? from : Vec3.lerp(from, to, (double) i / steps);
			BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
			String b = blocks.get(pos);
			if (b != null && !b.equals("minecraft:air") && !b.equals("minecraft:water") && !b.equals("minecraft:fire")) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	private void change(BlockPos pos, String block, String cause, int tick) {
		blocks.put(pos, block);
		changes.add(new Change(cause, tick, pos, block));
	}

	@Override
	public void destroy(BlockPos pos, String cause, int tick) {
		change(pos, "minecraft:air", cause, tick);
	}

	@Override
	public void lightFire(BlockPos pos, String cause, int tick) {
		change(pos, "minecraft:fire", cause, tick);
	}

	@Override
	public boolean place(BlockPos pos, String blockState, String cause, int tick) {
		change(pos, blockState, cause, tick);
		return true;
	}

	@Override
	public boolean use(BlockPos pos, String cause, int tick) {
		String b = blocks.get(pos);
		if (b == null || !b.startsWith("minecraft:oak_door")) {
			return false;
		}
		change(pos, b.endsWith("[open=true]") ? "minecraft:oak_door" : "minecraft:oak_door[open=true]", cause, tick);
		return true;
	}

	@Override
	public void reset() {
		blocks.clear();
		blocks.putAll(base);
		changes.clear();
	}
}
