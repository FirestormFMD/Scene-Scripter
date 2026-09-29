package io.github.firestormfmd.scenescripter.test;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.gametest.framework.GameTestHelper;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.firestormfmd.scenescripter.actor.Actors;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.server.SceneSession;

/** Actors: spawned, frozen, driven by the timeline, and left alone by everything else. */
public class ActorGameTests {
	@GameTest(maxTicks = 40)
	public void actorsAreFrozenAndNeverSaved(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		Vec3 start = TestScenes.at(helper, 2.5, 4, 2.5);
		TestScenes.object(scene, "o1", "minecraft:zombie", start);
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		Entity zombie = session.actors().actor("o1").orElseThrow(() -> helper.assertionException("zombie was not spawned"));
		helper.assertTrue(Actors.isActor(zombie), "actor is marked");
		helper.assertTrue(zombie instanceof Mob mob && mob.isNoAi(), "actor has no AI");
		helper.assertFalse(zombie.shouldBeSaved(), "actors are never saved");
		helper.assertFalse(zombie.isPickable(), "actors cannot be picked");
		helper.assertFalse(zombie.isPushable(), "actors cannot be pushed");
		helper.runAfterDelay(20, () -> {
			// No gravity, because actors never tick.
			helper.assertValueEqual(zombie.getY(), start.y(), "height after 20 ticks in mid-air");
			session.close();
			helper.assertTrue(zombie.isRemoved(), "closing the scene removes actors");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void playbackFollowsKeyframes(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		SceneObject o = TestScenes.object(scene, "o1", "minecraft:pig", TestScenes.at(helper, 1, 2, 1));
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(0, TestScenes.at(helper, 1, 2, 1)));
		o.channel(BuiltInChannels.POSITION).put(Keyframe.of(20, TestScenes.at(helper, 5, 2, 1)));
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		session.play();
		for (int i = 0; i < 10; i++) {
			session.tick();
		}
		Entity pig = session.actors().actor("o1").orElseThrow();
		double expectedX = TestScenes.at(helper, 3, 2, 1).x();
		helper.assertTrue(Math.abs(pig.getX() - expectedX) < 1e-6, "pig is halfway at tick 10, x=" + pig.getX());
		session.close();
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void deathThenRevive(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		SceneObject o = TestScenes.object(scene, "o1", "minecraft:zombie", TestScenes.at(helper, 2, 2, 2));
		o.channel(BuiltInChannels.DEAD).put(Keyframe.of(5, true, Interpolation.STEP));
		o.channel(BuiltInChannels.DEAD).put(Keyframe.of(60, false, Interpolation.STEP));
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		session.seek(10);
		Entity dying = session.actors().actor("o1").orElseThrow(() -> helper.assertionException("body should still be there"));
		helper.assertTrue(dying instanceof LivingEntity l && l.getHealth() <= 0, "dying zombie has no health");
		session.seek(40);
		helper.assertTrue(session.actors().actor("o1").isEmpty(), "body is gone after the death animation");
		session.seek(70);
		Entity revived = session.actors().actor("o1").orElseThrow(() -> helper.assertionException("zombie was not revived"));
		helper.assertTrue(revived != dying, "revival uses a fresh entity");
		helper.assertTrue(revived instanceof LivingEntity l && l.getHealth() > 0, "revived zombie is alive");
		session.close();
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void playerObjectsAreMannequins(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		SceneObject o = TestScenes.object(scene, "o1", "minecraft:mannequin", TestScenes.at(helper, 2, 2, 2));
		o.appearance().put("skin", "Notch");
		o.channel(BuiltInChannels.MAINHAND).setDefaultValue("minecraft:diamond_sword");
		o.channel(BuiltInChannels.SNEAKING).setDefaultValue(true);
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		Entity e = session.actors().actor("o1").orElseThrow();
		helper.assertTrue(e instanceof Mannequin, "player objects use mannequins");
		helper.assertTrue(((LivingEntity) e).getMainHandItem().getItem().toString().contains("diamond_sword"),
				"equipment applied: " + ((LivingEntity) e).getMainHandItem());
		helper.assertTrue(e.isShiftKeyDown(), "sneaking applied");
		session.close();
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void removedObjectsLoseTheirActor(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		TestScenes.object(scene, "o1", "minecraft:cow", TestScenes.at(helper, 2, 2, 2));
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		Entity cow = session.actors().actor("o1").orElseThrow();
		session.perform(new io.github.firestormfmd.scenescripter.core.edit.Edits.RemoveObject("o1"));
		session.tick();
		helper.assertTrue(cow.isRemoved(), "deleting the object removes its actor");
		session.close();
		helper.succeed();
	}
}
