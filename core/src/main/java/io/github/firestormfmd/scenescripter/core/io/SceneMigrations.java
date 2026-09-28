package io.github.firestormfmd.scenescripter.core.io;

import com.google.gson.JsonObject;

import io.github.firestormfmd.scenescripter.core.scene.Scene;

/**
 * Upgrades scene files saved by older versions to the current format, one version at a time.
 */
final class SceneMigrations {
	private SceneMigrations() {
	}

	static JsonObject upgrade(JsonObject root) throws SceneFormatException {
		if (!root.has("format")) {
			throw new SceneFormatException("Not a scene file: missing \"format\"");
		}
		int format = root.get("format").getAsInt();
		if (format > Scene.FORMAT) {
			throw new SceneFormatException("This scene was saved by a newer version of Scene Scripter (format "
					+ format + "); update the mod to open it");
		}
		if (format < 1) {
			throw new SceneFormatException("Unknown scene format " + format);
		}
		// Add a step here for each format change: while (format < Scene.FORMAT) { ...; format++; }
		root.addProperty("format", Scene.FORMAT);
		return root;
	}
}
