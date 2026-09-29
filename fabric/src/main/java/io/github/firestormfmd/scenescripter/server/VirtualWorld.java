package io.github.firestormfmd.scenescripter.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

import io.github.firestormfmd.scenescripter.core.journal.BlockChange;
import io.github.firestormfmd.scenescripter.core.journal.ChangeSet;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.solve.BlockWorld;

/**
 * The solver's copy of a level. It reads the scene's base state (the live level, with every block the scene has
 * changed read back from the journal's originals) and layers the solver's own changes on top, so a solve gives the
 * same result wherever the playhead is. The changes come out as the block journal's change sets.
 */
public final class VirtualWorld implements BlockWorld, BlockGetter {
	private final ServerLevel level;
	private final Map<BlockPos, SavedBlock> originals = new HashMap<>();
	private final Map<BlockPos, BlockState> overlay = new HashMap<>();
	private final Map<String, Pending> sets = new LinkedHashMap<>();

	private static final class Pending {
		final int tick;
		final List<BlockChange<SavedBlock>> changes = new ArrayList<>();

		Pending(int tick) {
			this.tick = tick;
		}
	}

	/**
	 * @param originals what the scene's applied block changes replaced, from {@code BlockJournal.originals()}
	 */
	public VirtualWorld(ServerLevel level, Map<io.github.firestormfmd.scenescripter.core.math.BlockPos, SavedBlock> originals) {
		this.level = level;
		originals.forEach((p, b) -> this.originals.put(new BlockPos(p.x(), p.y(), p.z()), b));
	}

	private static BlockPos mc(io.github.firestormfmd.scenescripter.core.math.BlockPos p) {
		return new BlockPos(p.x(), p.y(), p.z());
	}

	/** The block before the scene changed anything. Unloaded chunks read as air so solving never loads chunks. */
	public BlockState baseState(BlockPos pos) {
		SavedBlock original = originals.get(pos);
		if (original != null) {
			return original.state();
		}
		return level.isLoaded(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
	}

	/** The block changes the solve made, one set per event, for the block journal. */
	public List<ChangeSet<SavedBlock>> changeSets() {
		List<ChangeSet<SavedBlock>> out = new ArrayList<>();
		sets.forEach((id, p) -> out.add(new ChangeSet<>(id, p.tick, p.changes)));
		return out;
	}

	// ---- BlockGetter, so vanilla ray casts see the virtual world ----

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return overlay.containsKey(pos) || originals.containsKey(pos) || !level.isLoaded(pos) ? null : level.getBlockEntity(pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		BlockState changed = overlay.get(pos);
		return changed != null ? changed : baseState(pos);
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public int getHeight() {
		return level.getHeight();
	}

	@Override
	public int getMinY() {
		return level.getMinY();
	}

	// ---- BlockWorld ----

	@Override
	public float explosionResistance(io.github.firestormfmd.scenescripter.core.math.BlockPos pos) {
		BlockState state = getBlockState(mc(pos));
		FluidState fluid = state.getFluidState();
		if (state.isAir() && fluid.isEmpty()) {
			return -1;
		}
		return Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance());
	}

	@Override
	public boolean inBuildHeight(int y) {
		return y >= level.getMinY() && y < level.getMinY() + level.getHeight();
	}

	@Override
	public boolean isTnt(io.github.firestormfmd.scenescripter.core.math.BlockPos pos) {
		return getBlockState(mc(pos)).is(Blocks.TNT);
	}

	@Override
	public boolean canLightFire(io.github.firestormfmd.scenescripter.core.math.BlockPos pos) {
		BlockPos p = mc(pos);
		return getBlockState(p).isAir() && getBlockState(p.below()).isSolidRender();
	}

	@Override
	public Optional<Vec3> clip(Vec3 from, Vec3 to) {
		BlockHitResult hit = clip(new ClipContext(new net.minecraft.world.phys.Vec3(from.x(), from.y(), from.z()),
				new net.minecraft.world.phys.Vec3(to.x(), to.y(), to.z()), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.MISS) {
			return Optional.empty();
		}
		net.minecraft.world.phys.Vec3 at = hit.getLocation();
		return Optional.of(new Vec3(at.x, at.y, at.z));
	}

	@Override
	public void destroy(io.github.firestormfmd.scenescripter.core.math.BlockPos pos, String cause, int tick) {
		change(mc(pos), Blocks.AIR.defaultBlockState(), cause, tick);
	}

	@Override
	public void lightFire(io.github.firestormfmd.scenescripter.core.math.BlockPos pos, String cause, int tick) {
		BlockPos p = mc(pos);
		change(p, BaseFireBlock.getState(this, p), cause, tick);
	}

	@Override
	public boolean place(io.github.firestormfmd.scenescripter.core.math.BlockPos pos, String blockState, String cause, int tick) {
		try {
			BlockState state = BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), blockState, false)
					.blockState();
			change(mc(pos), state, cause, tick);
			return true;
		} catch (CommandSyntaxException e) {
			SceneScripter.LOGGER.warn("Scene block event {} has a bad block state: {}", cause, blockState);
			return false;
		}
	}

	@Override
	public boolean use(io.github.firestormfmd.scenescripter.core.math.BlockPos pos, String cause, int tick) {
		BlockPos p = mc(pos);
		BlockState state = getBlockState(p);
		if (state.hasProperty(BlockStateProperties.OPEN)) {
			BlockState flipped = state.cycle(BlockStateProperties.OPEN);
			change(p, flipped, cause, tick);
			if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
				BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below();
				BlockState otherState = getBlockState(other);
				if (otherState.is(state.getBlock()) && otherState.hasProperty(BlockStateProperties.OPEN)) {
					change(other, otherState.setValue(BlockStateProperties.OPEN, flipped.getValue(BlockStateProperties.OPEN)), cause, tick);
				}
			}
			return true;
		}
		if (state.hasProperty(BlockStateProperties.POWERED)) {
			change(p, state.cycle(BlockStateProperties.POWERED), cause, tick);
			return true;
		}
		return false;
	}

	@Override
	public void reset() {
		overlay.clear();
		sets.clear();
	}

	private void change(BlockPos pos, BlockState state, String cause, int tick) {
		BlockPos p = pos.immutable();
		overlay.put(p, state);
		sets.computeIfAbsent(cause, c -> new Pending(tick)).changes
				.add(new BlockChange<>(new io.github.firestormfmd.scenescripter.core.math.BlockPos(p.getX(), p.getY(), p.getZ()),
						new SavedBlock(state, null)));
	}
}
