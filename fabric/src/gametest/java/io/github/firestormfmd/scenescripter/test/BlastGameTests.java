package io.github.firestormfmd.scenescripter.test;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.server.SceneSession;

/** Explosions, chain reactions, shots and block events against a real level, and rewinding all of it. */
public class BlastGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
			}
		}
	}

	private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (int x = 0; x < 7; x++) {
			for (int y = 0; y < 5; y++) {
				for (int z = 0; z < 7; z++) {
					BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
					out.put(p, helper.getLevel().getBlockState(p));
				}
			}
		}
		return out;
	}

	private static void assertSame(GameTestHelper helper, Map<BlockPos, BlockState> before, String when) {
		before.forEach((pos, state) -> helper.assertTrue(helper.getLevel().getBlockState(pos) == state,
				when + ": " + pos + " is " + helper.getLevel().getBlockState(pos) + ", was " + state));
	}

	@GameTest(maxTicks = 40)
	public void explosionsRewindCompletely(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.TNT);
		helper.setBlock(new BlockPos(1, 2, 5), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 2, 5)));
		chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
		Map<BlockPos, BlockState> before = snapshot(helper);

		Scene scene = new Scene("t", 200);
		SceneObject tnt = TestScenes.object(scene, "tnt", "minecraft:tnt", TestScenes.at(helper, 3.5, 2, 3.5));
		tnt.addEvent(new SceneEvent("boom", 5, "explode", null, Map.of("power", 3.0), null));
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		helper.assertTrue(session.actors().actor("tnt").orElse(null) instanceof PrimedTnt, "the TNT is a primed TNT");
		helper.assertFalse(session.explosions().isEmpty(), "the explosion was baked");
		helper.assertTrue(scene.object("boom:tnt0").isPresent(), "the TNT block in the blast chain-reacts");

		session.seek(10);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
		helper.assertTrue(session.actors().actor("tnt").isEmpty(), "the TNT is gone once it explodes");
		helper.assertTrue(session.actors().actor("boom:tnt0").isPresent(), "the chained TNT is lit");
		session.seek(100);

		session.seek(0);
		assertSame(helper, before, "after rewinding");
		ChestBlockEntity restored = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 2, 5)));
		helper.assertTrue(restored != null && restored.getItem(0).getCount() == 3, "chest contents survive");

		session.seek(100);
		session.close();
		assertSame(helper, before, "after closing");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void blockEventsPlayAndRewind(GameTestHelper helper) {
		floor(helper);
		Map<BlockPos, BlockState> before = snapshot(helper);
		Scene scene = new Scene("t", 200);
		SceneObject builder = TestScenes.object(scene, "b", "minecraft:mannequin", TestScenes.at(helper, 1.5, 2, 1.5));
		BlockPos place = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos dig = helper.absolutePos(new BlockPos(4, 1, 4));
		builder.addEvent(new SceneEvent("p", 5, "place_block", null,
				Map.of("x", place.getX(), "y", place.getY(), "z", place.getZ(), "block", "minecraft:oak_log[axis=x]"), null));
		builder.addEvent(new SceneEvent("x", 8, "break_block", null,
				Map.of("x", dig.getX(), "y", dig.getY(), "z", dig.getZ()), null));
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		session.seek(6);
		helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(3, 2, 3));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(4, 1, 4));
		session.seek(9);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));
		session.seek(0);
		assertSame(helper, before, "after rewinding");
		session.close();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void arrowsFlyAndHit(GameTestHelper helper) {
		floor(helper);
		Scene scene = new Scene("t", 200);
		SceneObject archer = TestScenes.object(scene, "a", "minecraft:skeleton", TestScenes.at(helper, 0.5, 2, 3.5));
		TestScenes.object(scene, "z", "minecraft:zombie", TestScenes.at(helper, 6.5, 2, 3.5));
		archer.addEvent(new SceneEvent("shot", 2, "shoot", "z", Map.of(), null));
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		helper.assertTrue(!session.attackResults().isEmpty() && session.attackResults().getFirst().hit(),
				"the arrow hits: " + session.attackResults());
		session.seek(3);
		helper.assertTrue(session.actors().actor("shot:projectile").orElse(null) instanceof AbstractArrow,
				"the arrow is a real arrow in flight");
		session.close();
		helper.succeed();
	}
}
