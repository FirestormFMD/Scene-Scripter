package io.github.firestormfmd.scenescripter.test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.server.SceneManager;
import io.github.firestormfmd.scenescripter.server.SceneSession;

/**
 * The Mannequin audit (plan, Phase 0 spike 2) on a real client: player objects drawing a bow, eating, raising a
 * shield and sleeping. Checks that each state reaches the client through vanilla packets, and leaves a screenshot
 * (also printed small to the log) to look them over.
 */
@SuppressWarnings("UnstableApiUsage")
public class MannequinClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode creative @a");
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("scene new poses 200");
			context.waitTicks(5);
			singleplayer.getServer().runOnServer(server -> {
				SceneSession session = SceneManager.get().orElseThrow().session().orElseThrow();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, 15);
				Vec3 at = new Vec3(player.getX(), player.getY(), player.getZ() + 4);
				add(session, "archer", at.add(-3, 0, 0), "minecraft:bow", "", "main", "standing");
				add(session, "eater", at.add(-1, 0, 0), "minecraft:golden_apple", "", "main", "standing");
				add(session, "guard", at.add(1, 0, 0), "minecraft:iron_sword", "minecraft:shield", "off", "standing");
				add(session, "sleeper", at.add(3, 0, 1), "", "", "", "sleeping");
			});
			context.waitFor(client -> mannequins(client).size() >= 4, 200);
			context.waitTicks(20);

			Map<String, String> problems = context.computeOnClient(client -> {
				Map<String, Mannequin> m = mannequins(client);
				Map<String, String> out = new HashMap<>();
				expect(out, "archer", m.get("archer").isUsingItem() && m.get("archer").getUseItem().is(Items.BOW),
						"is not drawing the bow");
				expect(out, "eater", m.get("eater").isUsingItem() && m.get("eater").getUseItem().is(Items.GOLDEN_APPLE),
						"is not eating");
				expect(out, "guard", m.get("guard").isUsingItem() && m.get("guard").getUsedItemHand() == InteractionHand.OFF_HAND,
						"is not raising the shield");
				expect(out, "sleeper", m.get("sleeper").getPose() == Pose.SLEEPING, "is not sleeping");
				return out;
			});
			Path shot = context.takeScreenshot("scenescripter-mannequin-poses");
			Screenshots.printThumbnail(shot, "mannequin-poses", 0.2, 0.25, 0.8, 0.85);
			if (!problems.isEmpty()) {
				throw new AssertionError("Mannequins on the client: " + problems);
			}
		}
	}

	private static void add(SceneSession session, String name, Vec3 at, String mainhand, String offhand, String use, String pose) {
		SceneObject o = new SceneObject(name, name, "minecraft:mannequin");
		o.channel(BuiltInChannels.POSITION).setDefaultValue(at);
		o.channel(BuiltInChannels.BODY_YAW).setDefaultValue(180.0);
		o.channel(BuiltInChannels.HEAD_YAW).setDefaultValue(180.0);
		o.channel(BuiltInChannels.CUSTOM_NAME).setDefaultValue(name);
		o.channel(BuiltInChannels.NAME_VISIBLE).setDefaultValue(true);
		o.channel(BuiltInChannels.MAINHAND).setDefaultValue(mainhand);
		o.channel(BuiltInChannels.OFFHAND).setDefaultValue(offhand);
		o.channel(BuiltInChannels.USE_ITEM).setDefaultValue(use);
		o.channel(BuiltInChannels.POSE).setDefaultValue(pose);
		session.perform(new Edits.AddObject(o, session.scene().objects().size()));
	}

	private static Map<String, Mannequin> mannequins(Minecraft client) {
		Map<String, Mannequin> out = new HashMap<>();
		if (client.level == null) {
			return out;
		}
		for (Entity e : client.level.entitiesForRendering()) {
			if (e instanceof Mannequin m && m.getCustomName() != null) {
				out.put(m.getCustomName().getString(), m);
			}
		}
		return out;
	}

	private static void expect(Map<String, String> problems, String who, boolean ok, String problem) {
		if (!ok) {
			problems.put(who, problem);
		}
	}
}
