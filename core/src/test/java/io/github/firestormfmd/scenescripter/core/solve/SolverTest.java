package io.github.firestormfmd.scenescripter.core.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.runtime.BodyProvider;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.AttackMode;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.InteractionRules;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

class SolverTest {
	private final Scene scene = new Scene("fight", 400);
	private final SceneEvaluator eval = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED);
	private final Solver solver = new Solver(CombatModel.DEFAULT);

	/** A player facing +Z (yaw 0) at the origin, and a zombie in front of it. */
	private SceneObject knight;
	private SceneObject zombie;

	private void setUp(double zombieDistance) {
		knight = new SceneObject("o1", "Knight", "minecraft:mannequin");
		knight.channel(BuiltInChannels.POSITION).setDefaultValue(new Vec3(0, 64, 0));
		knight.channel(BuiltInChannels.MAINHAND).setDefaultValue("minecraft:iron_sword");
		scene.addObject(knight);
		zombie = new SceneObject("o2", "Zombie", "minecraft:zombie");
		zombie.channel(BuiltInChannels.POSITION).setDefaultValue(new Vec3(0, 64, zombieDistance));
		scene.addObject(zombie);
	}

	private SceneEvent attack(int tick) {
		SceneEvent e = new SceneEvent("e" + tick, tick, "attack", "o2", Map.of(), null);
		knight.addEvent(e);
		return e;
	}

	@Test
	void hitInReachDamagesAndKnocksBack() {
		setUp(2);
		attack(20);
		List<Solver.AttackResult> results = solver.solve(scene, eval);
		assertTrue(results.getFirst().hit(), results.getFirst().reason());
		// Player base 1 + iron sword 5, on the ground so no crit.
		assertEquals(6.0, results.getFirst().damage(), 1e-9);
		assertEquals(14.0, eval.evaluate(zombie, 20).health(), 1e-9);
		assertEquals(20.0, eval.evaluate(zombie, 10).health(), 1e-9);
		assertTrue(zombie.findEvent("e20:hurt").isPresent());
		assertTrue(eval.evaluate(zombie, 60).position().z() > 2.3, "knocked away from the knight");
		assertEquals(2.0, eval.evaluate(zombie, 19).position().z(), 1e-9);
	}

	@Test
	void outOfReachMisses() {
		setUp(6);
		attack(20);
		Solver.AttackResult r = solver.solve(scene, eval).getFirst();
		assertFalse(r.hit());
		assertEquals("Out of reach", r.reason());
		assertEquals(20.0, eval.evaluate(zombie, 30).health(), 1e-9);
	}

	@Test
	void mustFaceTheTarget() {
		setUp(-2);
		attack(20);
		assertEquals("Not facing the target", solver.solve(scene, eval).getFirst().reason());
	}

	@Test
	void alwaysHitIgnoresReach() {
		setUp(10);
		knight.setRules(InteractionRules.INHERIT.withAttack(AttackMode.ALWAYS_HIT));
		attack(20);
		assertTrue(solver.solve(scene, eval).getFirst().hit());
	}

	@Test
	void animationOnlyChangesNothing() {
		setUp(2);
		scene.setSettings(scene.settings().withRules(scene.settings().rules().withAttack(AttackMode.ANIMATION_ONLY)));
		attack(20);
		assertFalse(solver.solve(scene, eval).getFirst().hit());
		assertTrue(zombie.channel(BuiltInChannels.HEALTH).isEmpty());
	}

	@Test
	void armorReducesDamage() {
		setUp(2);
		zombie.channel(BuiltInChannels.CHEST).setDefaultValue("minecraft:iron_chestplate");
		attack(20);
		double dealt = solver.solve(scene, eval).getFirst().damage();
		assertEquals(VanillaCombat.afterArmor(6, 6, 0), dealt, 1e-9);
		assertTrue(dealt < 6);
	}

	@Test
	void hitCooldownBlocksRapidHits() {
		setUp(2);
		// No knockback, so the zombie stays in reach and only the cooldown matters.
		knight.setRules(new InteractionRules(null, 0.0, null, null, null, null, null));
		attack(20);
		attack(25);
		attack(35);
		List<Solver.AttackResult> r = solver.solve(scene, eval);
		assertTrue(r.get(0).hit());
		assertFalse(r.get(1).hit(), "within 10 ticks of the last hit");
		assertTrue(r.get(2).hit(), "the cooldown is over");
	}

	@Test
	void enoughHitsKill() {
		setUp(1.5);
		knight.setRules(InteractionRules.INHERIT.withAttack(AttackMode.ALWAYS_HIT));
		for (int t = 20; t <= 80; t += 20) {
			attack(t);
		}
		solver.solve(scene, eval);
		// 6 damage a hit: 14, 8, 2, then below zero on the fourth hit.
		assertFalse(eval.evaluate(zombie, 79).dead());
		assertTrue(eval.evaluate(zombie, 80).dead());
		assertEquals(0, eval.evaluate(zombie, 80).ticksDead());
	}

	@Test
	void knockbackCanPushTargetsOutOfReach() {
		setUp(2);
		attack(20);
		attack(40);
		List<Solver.AttackResult> r = solver.solve(scene, eval);
		assertTrue(r.get(0).hit());
		assertEquals("Out of reach", r.get(1).reason());
	}

	@Test
	void solvingTwiceGivesTheSameResult() {
		setUp(2);
		attack(20);
		attack(40);
		solver.solve(scene, eval);
		String first = io.github.firestormfmd.scenescripter.core.io.SceneCodec.write(scene);
		solver.solve(scene, eval);
		assertEquals(first, io.github.firestormfmd.scenescripter.core.io.SceneCodec.write(scene));
	}

	@Test
	void userKeysAreNeverOverwritten() {
		setUp(2);
		attack(20);
		zombie.channel(BuiltInChannels.HEALTH).put(Keyframe.of(20, 19.0, Interpolation.STEP));
		solver.solve(scene, eval);
		assertEquals(19.0, eval.evaluate(zombie, 20).health(), 1e-9);
	}

	@Test
	void knockbackMatchesVanillaDistance() {
		Vec3[] path = VanillaCombat.knockbackPath(new Vec3(0, 0, 1), 0.4);
		Vec3 end = path[path.length - 1];
		assertEquals(0, end.y(), 1e-9);
		assertTrue(end.z() > 1.5 && end.z() < 4, "knockback travels a couple of blocks: " + end.z());
	}
}
