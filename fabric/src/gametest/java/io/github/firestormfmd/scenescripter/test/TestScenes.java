package io.github.firestormfmd.scenescripter.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.gametest.framework.GameTestHelper;

import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.server.JournalFile;
import io.github.firestormfmd.scenescripter.server.SceneSession;

/** Helpers for building small scenes inside a game test's area. */
final class TestScenes {
	private TestScenes() {
	}

	static Path tempJournal() {
		try {
			return Files.createTempDirectory("scenescripter-test").resolve("journal.json.gz");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static SceneSession session(GameTestHelper helper, Scene scene) {
		return new SceneSession("test", scene, helper.getLevel(), new JournalFile(tempJournal(), "minecraft:overworld"));
	}

	/** Absolute scene coordinates for a point relative to the test area. */
	static Vec3 at(GameTestHelper helper, double x, double y, double z) {
		net.minecraft.world.phys.Vec3 v = helper.absoluteVec(new net.minecraft.world.phys.Vec3(x, y, z));
		return new Vec3(v.x, v.y, v.z);
	}

	static SceneObject object(Scene scene, String id, String type, Vec3 at) {
		SceneObject o = new SceneObject(id, id, type);
		o.channel(BuiltInChannels.POSITION).setDefaultValue(at);
		scene.addObject(o);
		return o;
	}
}
