package io.github.firestormfmd.scenescripter.test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.firestormfmd.scenescripter.core.journal.BlockChange;
import io.github.firestormfmd.scenescripter.core.journal.BlockJournal;
import io.github.firestormfmd.scenescripter.core.journal.ChangeSet;
import io.github.firestormfmd.scenescripter.core.path.BodySettings;
import io.github.firestormfmd.scenescripter.core.path.Locomotion;
import io.github.firestormfmd.scenescripter.core.path.LocomotionPlanner;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.GroundFilter;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.core.solve.Solver;
import io.github.firestormfmd.scenescripter.server.EntityBodies;
import io.github.firestormfmd.scenescripter.server.JournalFile;
import io.github.firestormfmd.scenescripter.server.LevelTerrain;
import io.github.firestormfmd.scenescripter.server.SavedBlock;
import io.github.firestormfmd.scenescripter.server.WorldBlocks;
import io.github.firestormfmd.scenescripter.server.WorldCombat;

/** The scene's effects on the world: block journal, crash recovery, ground snapping and combat. */
public class WorldGameTests {
	private static io.github.firestormfmd.scenescripter.core.math.BlockPos core(BlockPos p) {
		return new io.github.firestormfmd.scenescripter.core.math.BlockPos(p.getX(), p.getY(), p.getZ());
	}

	@GameTest(maxTicks = 20)
	public void journalRestoresBlocksAndChestContents(GameTestHelper helper) {
		BlockPos stone = new BlockPos(2, 1, 2);
		BlockPos chest = new BlockPos(3, 1, 2);
		helper.setBlock(stone, Blocks.STONE);
		helper.setBlock(chest, Blocks.CHEST);
		ChestBlockEntity box = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chest));
		box.setItem(0, new ItemStack(Items.DIAMOND, 5));

		WorldBlocks blocks = new WorldBlocks(helper.getLevel());
		SavedBlock air = new SavedBlock(Blocks.AIR.defaultBlockState(), null);
		BlockJournal<SavedBlock> journal = new BlockJournal<>(new JournalFile(TestScenes.tempJournal(), "minecraft:overworld"));
		journal.setChangeSets(List.of(new ChangeSet<>("boom", 5, List.of(
				new BlockChange<>(core(helper.absolutePos(stone)), air),
				new BlockChange<>(core(helper.absolutePos(chest)), air)))), blocks);

		journal.seek(10, blocks);
		helper.assertBlockPresent(Blocks.AIR, stone);
		helper.assertBlockPresent(Blocks.AIR, chest);

		journal.seek(0, blocks);
		helper.assertBlockPresent(Blocks.STONE, stone);
		helper.assertBlockPresent(Blocks.CHEST, chest);
		ChestBlockEntity restored = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chest));
		helper.assertTrue(restored != null && restored.getItem(0).is(Items.DIAMOND) && restored.getItem(0).getCount() == 5,
				"chest contents restored");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void crashRecoveryUndoesLeftoverChanges(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, Blocks.GOLD_BLOCK);
		Path file = TestScenes.tempJournal();
		WorldBlocks blocks = new WorldBlocks(helper.getLevel());
		BlockJournal<SavedBlock> journal = new BlockJournal<>(new JournalFile(file, "minecraft:overworld"));
		journal.setChangeSets(List.of(new ChangeSet<>("boom", 1, List.of(
				new BlockChange<>(core(helper.absolutePos(pos)), new SavedBlock(Blocks.AIR.defaultBlockState(), null))))), blocks);
		journal.seek(5, blocks);
		helper.assertBlockPresent(Blocks.AIR, pos);

		// The session is gone; only the file is left, as after a crash.
		JournalFile.Leftover leftover = JournalFile.read(file).orElseThrow(() -> helper.assertionException("journal file missing"));
		BlockJournal.restore(leftover.sets(), blocks);
		helper.assertBlockPresent(Blocks.GOLD_BLOCK, pos);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void groundPathsJumpOntoRealBlocks(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			helper.setBlock(new BlockPos(x, 0, 1), Blocks.STONE);
		}
		for (int x = 4; x < 8; x++) {
			helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
		}
		MotionPath path = new MotionPath("p1", "p", PathKind.GROUND);
		path.points().add(PathPoint.at(TestScenes.at(helper, 0.5, 1, 1.5)));
		path.points().add(PathPoint.at(TestScenes.at(helper, 7.5, 1, 1.5)));
		Locomotion loco = LocomotionPlanner.plan(path, MotionClip.atSpeed("p1", 0), BodySettings.PLAYER,
				new LevelTerrain(helper.getLevel(), GroundFilter.NATURAL_GROUND), GroundFilter.NATURAL_GROUND);
		helper.assertTrue(loco.issues().isEmpty(), "no path problems: " + loco.issues());
		helper.assertValueEqual(loco.jumpTicks().size(), 1, "jumps");
		double top = TestScenes.at(helper, 0, 2, 0).y();
		helper.assertTrue(Math.abs(loco.samples().getLast().pos().y() - top) < 1e-6,
				"ends on top of the step at y=" + loco.samples().getLast().pos().y());
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void autoAttacksUseRealAttributes(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		SceneObject knight = TestScenes.object(scene, "o1", "minecraft:mannequin", TestScenes.at(helper, 2, 1, 1));
		knight.channel(io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.MAINHAND).setDefaultValue("minecraft:iron_sword");
		TestScenes.object(scene, "o2", "minecraft:zombie", TestScenes.at(helper, 2, 1, 3));
		knight.addEvent(new SceneEvent("e1", 10, "attack", "o2", Map.of(), null));
		SceneEvaluator eval = new SceneEvaluator(scene, null, EntityBodies.INSTANCE);
		List<Solver.AttackResult> results = new Solver(new WorldCombat(helper.getLevel())).solve(scene, eval);
		helper.assertTrue(results.getFirst().hit(), "attack should land: " + results.getFirst().reason());
		helper.assertValueEqual(eval.evaluate("o2", 10).orElseThrow().health(), 14.0, "zombie health after an iron sword hit");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void wallsBlockAttacks(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int y = 1; y < 4; y++) {
				helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
			}
		}
		Scene scene = new Scene("t", 200);
		SceneObject knight = TestScenes.object(scene, "o1", "minecraft:mannequin", TestScenes.at(helper, 2, 1, 1));
		TestScenes.object(scene, "o2", "minecraft:zombie", TestScenes.at(helper, 2, 1, 3.5));
		knight.addEvent(new SceneEvent("e1", 10, "attack", "o2", Map.of(), null));
		SceneEvaluator eval = new SceneEvaluator(scene, null, EntityBodies.INSTANCE);
		List<Solver.AttackResult> results = new Solver(new WorldCombat(helper.getLevel())).solve(scene, eval);
		helper.assertFalse(results.getFirst().hit(), "the wall is in the way");
		helper.succeed();
	}
}
