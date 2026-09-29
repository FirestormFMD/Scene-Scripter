package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Map;

import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * A short example scene that shows each feature once: a knight charges along a path at dusk, a skeleton fires
 * at him, a TNT goes off beside the road, he cuts down a zombie, and thunder rolls in. Built around an origin so
 * it can be placed anywhere; open it, press Play and look at how each part is set up.
 */
public final class TutorialScene {
	private TutorialScene() {
	}

	public static Scene build(String name, BlockPos origin) {
		Scene scene = new Scene(name, 240);
		scene.setOrigin(origin);
		Vec3 o = new Vec3(origin.x() + 0.5, origin.y(), origin.z() + 0.5);

		MotionPath charge = new MotionPath("p1", "Charge", PathKind.GROUND);
		charge.points().add(PathPoint.at(o));
		charge.points().add(PathPoint.at(o.add(0, 0, 12)));
		charge.points().add(PathPoint.at(o.add(5, 0, 19)));
		charge.setGait(Gait.SPRINT);
		charge.setBaseSpeed(Gait.SPRINT.playerSpeed());
		scene.addPath(charge);

		SceneObject knight = new SceneObject("o1", "Knight", "minecraft:mannequin");
		knight.channel(BuiltInChannels.POSITION).setDefaultValue(o);
		knight.channel(BuiltInChannels.MAINHAND).setDefaultValue("minecraft:iron_sword");
		knight.channel(BuiltInChannels.CHEST).setDefaultValue("minecraft:iron_chestplate");
		knight.channel(BuiltInChannels.HEAD).setDefaultValue("minecraft:iron_helmet");
		knight.addMotion(MotionClip.atSpeed("p1", 20));
		knight.channel(BuiltInChannels.LOOK_AT).put(Keyframe.of(90, "o3", Interpolation.STEP));
		for (int t : new int[] {112, 124, 136}) {
			knight.addEvent(new SceneEvent("e" + t, t, "attack", "o2", Map.of("hand", "main"), null));
		}
		scene.addObject(knight);

		SceneObject zombie = new SceneObject("o2", "Zombie", "minecraft:zombie");
		zombie.channel(BuiltInChannels.POSITION).setDefaultValue(o.add(6, 0, 21));
		zombie.channel(BuiltInChannels.BODY_YAW).setDefaultValue(150.0);
		zombie.channel(BuiltInChannels.HEALTH).setDefaultValue(20.0);
		zombie.putChannel("aggressive", new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
				io.github.firestormfmd.scenescripter.core.anim.ValueType.BOOL, true));
		scene.addObject(zombie);

		SceneObject archer = new SceneObject("o3", "Archer", "minecraft:skeleton");
		archer.channel(BuiltInChannels.POSITION).setDefaultValue(o.add(-9, 0, 24));
		archer.channel(BuiltInChannels.MAINHAND).setDefaultValue("minecraft:bow");
		archer.channel(BuiltInChannels.LOOK_AT).setDefaultValue("o1");
		archer.channel(BuiltInChannels.USE_ITEM).put(Keyframe.of(60, "main", Interpolation.STEP));
		archer.channel(BuiltInChannels.USE_ITEM).put(Keyframe.of(80, "", Interpolation.STEP));
		archer.addEvent(new SceneEvent("e80", 80, "shoot", "o1", Map.of(), null));
		scene.addObject(archer);

		SceneObject tnt = new SceneObject("o4", "TNT", "minecraft:tnt");
		tnt.channel(BuiltInChannels.POSITION).setDefaultValue(o.add(-5, 0, 9));
		tnt.setLifetime(20, -1);
		tnt.addEvent(new SceneEvent("e100", 100, "explode", null, Map.of("power", 3.0), null));
		scene.addObject(tnt);

		SceneObject track = scene.tracks();
		track.channel(BuiltInChannels.TIME_OF_DAY).put(Keyframe.of(0, 12000, Interpolation.LINEAR));
		track.channel(BuiltInChannels.TIME_OF_DAY).put(Keyframe.of(240, 13000, Interpolation.LINEAR));
		track.channel(BuiltInChannels.WEATHER).put(Keyframe.of(150, "thunder", Interpolation.STEP));
		track.addEvent(new SceneEvent("e20", 20, "marker", null, Map.of("name", "Charge"), null));
		track.addEvent(new SceneEvent("e150", 150, "sound", null, Map.of("sound", "entity.lightning_bolt.thunder"), null));
		for (String prefix : new String[] {"o", "p", "e"}) {
			scene.setIdCounter(prefix, 200);
		}
		return scene;
	}
}
