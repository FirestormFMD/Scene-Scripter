package io.github.firestormfmd.scenescripter.test;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.gametest.framework.GameTestHelper;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.firestormfmd.scenescripter.actor.Actors;
import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.anim.ValueType;
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
	public void mobCapabilitiesReachActors(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		SceneObject creeper = TestScenes.object(scene, "creeper", "minecraft:creeper", TestScenes.at(helper, 1, 2, 1));
		creeper.appearance().put("powered", "true");
		SceneObject wolf = TestScenes.object(scene, "wolf", "minecraft:wolf", TestScenes.at(helper, 3, 2, 1));
		wolf.putChannel("tame", new Channel<>(ValueType.BOOL, true));
		wolf.putChannel("angry", new Channel<>(ValueType.BOOL, true));
		wolf.putChannel("collar_color", new Channel<>(ValueType.ENUM, "blue"));
		SceneObject villager = TestScenes.object(scene, "villager", "minecraft:villager", TestScenes.at(helper, 5, 2, 1));
		villager.putChannel("profession", new Channel<>(ValueType.ENUM, "librarian"));
		villager.putChannel("villager_type", new Channel<>(ValueType.ENUM, "desert"));
		SceneObject horse = TestScenes.object(scene, "horse", "minecraft:horse", TestScenes.at(helper, 1, 2, 4));
		horse.putChannel("rearing", new Channel<>(ValueType.BOOL, true));
		SceneObject enderman = TestScenes.object(scene, "enderman", "minecraft:enderman", TestScenes.at(helper, 4, 2, 4));
		enderman.putChannel("screaming", new Channel<>(ValueType.BOOL, true));
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		helper.assertTrue(session.actors().actor("creeper").orElseThrow() instanceof Creeper c && c.isPowered(), "creeper is charged");
		Wolf w = (Wolf) session.actors().actor("wolf").orElseThrow();
		helper.assertTrue(w.isTame(), "wolf is tame");
		helper.assertTrue(w.isAngry(), "wolf is angry");
		helper.assertValueEqual(w.getCollarColor(), DyeColor.BLUE, "collar color");
		Villager v = (Villager) session.actors().actor("villager").orElseThrow();
		helper.assertTrue(v.getVillagerData().profession().is(VillagerProfession.LIBRARIAN), "villager is a librarian");
		helper.assertTrue(v.getVillagerData().type().is(VillagerType.DESERT), "villager is from the desert");
		helper.assertTrue(((AbstractHorse) session.actors().actor("horse").orElseThrow()).isStanding(), "horse rears");
		helper.assertTrue(((EnderMan) session.actors().actor("enderman").orElseThrow()).isCreepy(), "enderman screams");
		session.close();
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void propsShowTheirContent(GameTestHelper helper) {
		Scene scene = new Scene("t", 200);
		TestScenes.object(scene, "block", "minecraft:falling_block", TestScenes.at(helper, 1, 2, 1))
				.appearance().put("block", "minecraft:gold_block");
		TestScenes.object(scene, "apple", "minecraft:item", TestScenes.at(helper, 3, 2, 1)).appearance().put("item", "minecraft:apple");
		TestScenes.object(scene, "kid", "minecraft:zombie", TestScenes.at(helper, 5, 2, 1)).appearance().put("baby", "true");
		TestScenes.object(scene, "sign", "minecraft:text_display", TestScenes.at(helper, 1, 3, 4)).appearance().put("text", "Hello");
		SceneSession session = TestScenes.session(helper, scene);
		session.tick();
		helper.assertTrue(session.actors().actor("block").orElseThrow() instanceof FallingBlockEntity f
				&& f.getBlockState().is(Blocks.GOLD_BLOCK), "falling block shows gold");
		helper.assertTrue(session.actors().actor("apple").orElseThrow() instanceof ItemEntity i && i.getItem().is(Items.APPLE),
				"dropped item is an apple");
		helper.assertTrue(session.actors().actor("kid").orElseThrow() instanceof Mob m && m.isBaby(), "zombie is a baby");
		helper.assertTrue(session.actors().actor("sign").isPresent(), "text display spawned");
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
