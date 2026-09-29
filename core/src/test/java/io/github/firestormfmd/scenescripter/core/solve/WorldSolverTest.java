package io.github.firestormfmd.scenescripter.core.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.io.SceneCodec;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.runtime.BodyProvider;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/** Explosions, projectiles and block events solved against a virtual world. */
class WorldSolverTest {
	private final Scene scene = new Scene("siege", 600);
	private final SceneEvaluator eval = new SceneEvaluator(scene, null, BodyProvider.PLAYER_SIZED);
	private final Solver solver = new Solver(CombatModel.DEFAULT);
	private final VoxelWorld world = new VoxelWorld().fill(-12, -3, -12, 12, 0, 12, "minecraft:dirt");

	private SceneObject object(String id, String type, Vec3 at) {
		SceneObject o = new SceneObject(id, id, type);
		o.channel(BuiltInChannels.POSITION).setDefaultValue(at);
		scene.addObject(o);
		return o;
	}

	@Test
	void tntExplodesWhenItsFuseRunsOut() {
		SceneObject tnt = object("tnt", "minecraft:tnt", new Vec3(0.5, 1, 0.5));
		tnt.setLifetime(10, -1);
		Solver.Solution s = solver.solve(scene, eval, world);
		assertEquals(1, s.explosions().size());
		Solver.ExplosionResult e = s.explosions().getFirst();
		assertEquals(90, e.tick());
		assertEquals("tnt:explode", e.eventId());
		assertTrue(tnt.findEvent("tnt:explode").isPresent(), "a generated explode event plays the effect");
		assertTrue(eval.evaluate(tnt, 89).exists());
		assertFalse(eval.evaluate(tnt, 90).exists(), "the TNT is gone once it explodes");
		assertFalse(e.blocks().isEmpty());
		assertTrue(world.changes.stream().allMatch(c -> c.tick() == 90 && c.block().equals("minecraft:air")));
		assertEquals("minecraft:air", world.get(0, 0, 0));
	}

	@Test
	void explodeEventOverridesTheFuseAndPower() {
		SceneObject tnt = object("tnt", "minecraft:tnt", new Vec3(0.5, 1, 0.5));
		tnt.addEvent(new SceneEvent("boom", 20, "explode", null, Map.of("power", 1.0), null));
		Solver.Solution s = solver.solve(scene, eval, world);
		assertEquals(1, s.explosions().size());
		assertEquals(20, s.explosions().getFirst().tick());
		assertEquals(1.0f, s.explosions().getFirst().power());
	}

	@Test
	void blastsHurtAndThrowNearbyMobs() {
		SceneObject tnt = object("tnt", "minecraft:tnt", new Vec3(0.5, 1, 0.5));
		tnt.addEvent(new SceneEvent("boom", 20, "explode", null, Map.of(), null));
		SceneObject zombie = object("z", "minecraft:zombie", new Vec3(3.5, 1, 0.5));
		SceneObject far = object("far", "minecraft:zombie", new Vec3(11.5, 1, 0.5));
		solver.solve(scene, eval, world);
		assertTrue(zombie.findEvent("boom:hurt").isPresent());
		assertTrue(eval.evaluate(zombie, 20).health() < 20);
		assertTrue(eval.evaluate(zombie, 40).position().x() > 3.6, "thrown away from the blast");
		assertTrue(far.events().isEmpty(), "out of range");
	}

	@Test
	void protectedBlocksSurvive() {
		SceneObject tnt = object("tnt", "minecraft:tnt", new Vec3(0.5, 1, 0.5));
		tnt.addEvent(new SceneEvent("boom", 20, "explode", null, Map.of("protect", "0 0 0; 1 0 0"), null));
		solver.solve(scene, eval, world);
		assertEquals("minecraft:dirt", world.get(0, 0, 0));
		assertEquals("minecraft:dirt", world.get(1, 0, 0));
		assertEquals("minecraft:air", world.get(0, 0, 1));
	}

	@Test
	void tntInTheBlastChainReacts() {
		world.set(3, 1, 0, "minecraft:tnt");
		SceneObject tnt = object("tnt", "minecraft:tnt", new Vec3(0.5, 1, 0.5));
		tnt.addEvent(new SceneEvent("boom", 20, "explode", null, Map.of(), null));
		Solver.Solution s = solver.solve(scene, eval, world);
		SceneObject chained = scene.object("boom:tnt0").orElseThrow();
		assertEquals("boom", chained.generatedBy());
		assertEquals(20, chained.spawnTick());
		int fuse = Integer.parseInt(chained.appearance().get("fuse"));
		assertTrue(fuse >= 10 && fuse < 30);
		assertEquals(2, s.explosions().size());
		assertEquals(20 + fuse, s.explosions().get(1).tick());

		// Solving again is repeatable: the generated TNT is rebuilt, not duplicated.
		Solver.Solution again = solver.solve(scene, eval, world);
		assertEquals(s.explosions(), again.explosions());
		assertEquals(2, scene.objects().size());
	}

	@Test
	void ignitedCreepersExplodeAfterTheirSwell() {
		SceneObject creeper = object("c", "minecraft:creeper", new Vec3(0.5, 1, 0.5));
		creeper.addEvent(new SceneEvent("hiss", 40, "ignite", null, Map.of(), null));
		Solver.Solution s = solver.solve(scene, eval, world);
		assertEquals(70, s.explosions().getFirst().tick());
		assertEquals(3.0f, s.explosions().getFirst().power());
		assertTrue((Boolean) creeper.channel(BuiltInChannels.IGNITED).valueAt(45));
		assertFalse((Boolean) creeper.channel(BuiltInChannels.IGNITED).valueAt(39));
		assertFalse(eval.evaluate(creeper, 70).exists());
	}

	@Test
	void defusedCreepersDontExplode() {
		SceneObject creeper = object("c", "minecraft:creeper", new Vec3(0.5, 1, 0.5));
		creeper.addEvent(new SceneEvent("hiss", 40, "ignite", null, Map.of(), null));
		creeper.addEvent(new SceneEvent("calm", 55, "defuse", null, Map.of(), null));
		Solver.Solution s = solver.solve(scene, eval, world);
		assertTrue(s.explosions().isEmpty());
		assertTrue((Boolean) creeper.channel(BuiltInChannels.IGNITED).valueAt(50));
		assertFalse((Boolean) creeper.channel(BuiltInChannels.IGNITED).valueAt(56));
		assertTrue(eval.evaluate(creeper, 80).exists());
	}

	@Test
	void arrowsFlyToTheirTargetAndHurtIt() {
		SceneObject archer = object("a", "minecraft:skeleton", new Vec3(0.5, 1, -8.5));
		SceneObject zombie = object("z", "minecraft:zombie", new Vec3(0.5, 1, 8.5));
		archer.addEvent(new SceneEvent("shot", 30, "shoot", "z", Map.of(), null));
		Solver.Solution s = solver.solve(scene, eval, world);
		Solver.AttackResult r = s.attacks().getFirst();
		assertTrue(r.hit(), r.reason());
		assertTrue(r.tick() > 30 && r.tick() < 40, "arrives a few ticks later: " + r.tick());
		assertTrue(r.damage() >= 6, "a full-power arrow hits hard: " + r.damage());
		SceneObject arrow = scene.object("shot:projectile").orElseThrow();
		assertEquals("minecraft:arrow", arrow.entityType());
		assertTrue(eval.evaluate(arrow, 31).exists());
		assertFalse(eval.evaluate(arrow, r.tick()).exists(), "the arrow is used up on the hit");
		assertTrue(zombie.findEvent("shot:hurt").isPresent());
		assertTrue(eval.evaluate(zombie, r.tick()).health() < 20);
	}

	@Test
	void wallsStopArrows() {
		world.fill(-3, 1, 0, 3, 5, 0, "minecraft:stone");
		SceneObject archer = object("a", "minecraft:skeleton", new Vec3(0.5, 1, -8.5));
		object("z", "minecraft:zombie", new Vec3(0.5, 1, 8.5));
		archer.addEvent(new SceneEvent("shot", 30, "shoot", "z", Map.of(), null));
		Solver.AttackResult r = solver.solve(scene, eval, world).attacks().getFirst();
		assertFalse(r.hit());
		assertEquals("Hit a block", r.reason());
		SceneObject arrow = scene.object("shot:projectile").orElseThrow();
		assertTrue(eval.evaluate(arrow, 200).exists(), "arrows stay stuck in the wall");
		assertTrue(eval.evaluate(arrow, 200).position().z() < 0.1);
	}

	@Test
	void blockEventsChangeTheVirtualWorldInOrder() {
		world.set(5, 1, 5, "minecraft:oak_door");
		SceneObject builder = object("b", "minecraft:mannequin", new Vec3(0, 1, 0));
		builder.addEvent(new SceneEvent("p", 10, "place_block", null,
				Map.of("x", 2, "y", 1, "z", 2, "block", "minecraft:stone"), null));
		builder.addEvent(new SceneEvent("u", 20, "use_block", null, Map.of("x", 5, "y", 1, "z", 5), null));
		builder.addEvent(new SceneEvent("x", 30, "break_block", null, Map.of("x", 2, "y", 1, "z", 2), null));
		solver.solve(scene, eval, world);
		assertEquals(List.of("p", "u", "x"), world.changes.stream().map(VoxelWorld.Change::cause).toList());
		assertEquals("minecraft:oak_door[open=true]", world.get(5, 1, 5));
		assertEquals("minecraft:air", world.get(2, 1, 2));
	}

	@Test
	void generatedObjectsSurviveASaveRoundTrip() {
		SceneObject archer = object("a", "minecraft:skeleton", new Vec3(0.5, 1, -8.5));
		object("z", "minecraft:zombie", new Vec3(0.5, 1, 8.5));
		archer.addEvent(new SceneEvent("shot", 30, "shoot", "z", Map.of(), null));
		solver.solve(scene, eval, world);
		SceneObject arrow = scene.object("shot:projectile").orElseThrow();
		SceneObject back = SceneCodec.readObject(SceneCodec.object(arrow));
		assertEquals("shot", back.generatedBy());
		assertTrue(back.isGenerated());
	}
}
