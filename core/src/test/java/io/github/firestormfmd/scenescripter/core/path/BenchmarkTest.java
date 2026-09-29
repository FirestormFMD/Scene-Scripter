package io.github.firestormfmd.scenescripter.core.path;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.crowd.CrowdBuilder;
import io.github.firestormfmd.scenescripter.core.crowd.Formation;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.runtime.BodyProvider;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.core.solve.CombatModel;
import io.github.firestormfmd.scenescripter.core.solve.Solver;

/**
 * The plan's benchmark scene: 200 actors marching on paths with a hundred attacks. Solving and evaluating every
 * actor on every tick must stay far below a server tick's budget.
 */
class BenchmarkTest {
	/** Endless flat ground with its top at y = 64. */
	private static final TerrainView FLAT = new TerrainView() {
		@Override
		public double groundTop(int x, int y, int z) {
			return y == 63 ? 1.0 : Double.NaN;
		}

		@Override
		public boolean obstructs(int x, int y, int z) {
			return y <= 63;
		}

		@Override
		public double fluidTop(int x, int y, int z) {
			return Double.NaN;
		}
	};

	@Test
	void twoHundredActorsFitInATick() {
		Scene scene = new Scene("benchmark", 600);
		MotionPath path = new MotionPath("p1", "March", PathKind.GROUND);
		path.points().add(PathPoint.at(0, 64, 0));
		path.points().add(PathPoint.at(0, 64, 60));
		path.points().add(PathPoint.at(30, 64, 90));
		scene.addPath(path);
		SceneObject leader = new SceneObject("o1", "Soldier", "minecraft:mannequin");
		leader.channel(BuiltInChannels.POSITION).setDefaultValue(new Vec3(0, 64, 0));
		leader.addMotion(MotionClip.atSpeed("p1", 20));
		scene.addObject(leader);
		for (SceneObject m : CrowdBuilder.build(leader, Formation.GRID, 199, 1.5, i -> "c" + i, 2, 4, 1)) {
			scene.addObject(m);
		}
		int n = 0;
		for (SceneObject o : scene.objects()) {
			if (n++ % 2 == 0) {
				o.addEvent(new SceneEvent(o.id() + "_hit", 100 + n, "attack", null, Map.of(), null));
			}
		}

		SceneEvaluator eval = new SceneEvaluator(scene, FLAT, BodyProvider.PLAYER_SIZED);
		long t0 = System.nanoTime();
		new Solver(CombatModel.DEFAULT).solve(scene, eval);
		long solved = System.nanoTime();
		for (int tick = 0; tick <= scene.length(); tick++) {
			for (SceneObject o : scene.objects()) {
				eval.evaluate(o, tick);
			}
		}
		long evaluated = System.nanoTime();
		double solveMs = (solved - t0) / 1e6;
		double perTickMs = (evaluated - solved) / 1e6 / (scene.length() + 1);
		System.out.printf("benchmark: %d actors, solve %.1f ms, evaluate %.3f ms per tick%n",
				scene.objects().size(), solveMs, perTickMs);
		assertTrue(scene.objects().size() == 200);
		assertTrue(perTickMs < 25, "evaluating 200 actors must fit well inside a 50 ms tick: " + perTickMs + " ms");
		assertTrue(solveMs < 20000, "solving the fight must not stall the editor: " + solveMs + " ms");
	}
}
