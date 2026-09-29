package io.github.firestormfmd.scenescripter.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Opens a world, creates a scene, opens the editor with Right Ctrl and takes screenshots. Checks that the mod loads
 * on a real client, that the editor opens and closes without crashing, and that its panels and the F1 shortcut list
 * actually draw: each changes a good part of the screen.
 */
@SuppressWarnings("UnstableApiUsage")
public class EditorClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode creative @a");
			singleplayer.getServer().runCommand("scene new demo 200");
			context.waitTicks(5);

			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_CONTROL);
			context.waitFor(client -> client.gui.screen() != null
					&& client.gui.screen().getClass().getSimpleName().equals("EditorScreen"), 100);
			context.waitTicks(5);
			Path open = context.takeScreenshot("scenescripter-editor-open");

			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.waitTicks(3);
			Path help = context.takeScreenshot("scenescripter-editor-help");
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.waitTicks(3);

			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_CONTROL);
			context.waitFor(client -> client.gui.screen() == null, 100);
			singleplayer.getServer().runCommand("scene close");
			context.waitTicks(5);
			Path closed = context.takeScreenshot("scenescripter-editor-closed");

			expectChanged(closed, open, 0.10, "the editor panels");
			expectChanged(open, help, 0.02, "the F1 shortcut list");
		}
	}

	/** Fails unless at least {@code share} of the pixels differ clearly between two screenshots. */
	private static void expectChanged(Path before, Path after, double share, String what) {
		double changed = changedShare(before, after);
		if (changed < share) {
			throw new AssertionError(String.format(java.util.Locale.ROOT, "Expected %s to change at least %.0f%% of the screen, but"
					+ " only %.1f%% changed", what, share * 100, changed * 100));
		}
	}

	private static double changedShare(Path a, Path b) {
		try {
			BufferedImage x = ImageIO.read(a.toFile());
			BufferedImage y = ImageIO.read(b.toFile());
			if (x.getWidth() != y.getWidth() || x.getHeight() != y.getHeight()) {
				return 1;
			}
			int changed = 0;
			int samples = 0;
			for (int py = 0; py < x.getHeight(); py += 4) {
				for (int px = 0; px < x.getWidth(); px += 4) {
					int p = x.getRGB(px, py);
					int q = y.getRGB(px, py);
					int d = Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF)) + Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF))
							+ Math.abs((p & 0xFF) - (q & 0xFF));
					if (d > 40) {
						changed++;
					}
					samples++;
				}
			}
			return samples == 0 ? 0 : (double) changed / samples;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
