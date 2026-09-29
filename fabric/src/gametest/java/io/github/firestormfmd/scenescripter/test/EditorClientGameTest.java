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
			int[] gui = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(),
					client.getWindow().getGuiScaledHeight()});
			matchesReference(open, "editor-open", gui[0], gui[1]);

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

			Screenshots.printThumbnail(open, "editor-open");
			expectChanged(closed, open, 0.10, "the editor panels");
			expectChanged(open, help, 0.02, "the F1 shortcut list");
		}
	}

	/**
	 * Compares the editor's panels in a screenshot against the reviewed reference fingerprint in
	 * {@code /screenshots/<name>.fingerprint}: only the grid cells covered by the top bar, outliner, inspector and
	 * timeline count, since the world behind the viewport differs from run to run. Prints the fingerprint so a new
	 * reference can be taken from the log after the screenshot has been looked at.
	 */
	private static void matchesReference(Path shot, String name, int guiWidth, int guiHeight) {
		int[] cells = Screenshots.fingerprint(shot);
		System.out.println("FINGERPRINT " + name + " " + Screenshots.hex(cells));
		String reference;
		try (var in = EditorClientGameTest.class.getResourceAsStream("/screenshots/" + name + ".fingerprint")) {
			if (in == null) {
				System.out.println("No reference for " + name + " yet");
				return;
			}
			reference = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int[] expected = Screenshots.unhex(reference);
		// Panel edges in grid cells, shrunk by one cell so the viewport's border never counts.
		int top = (int) ((20.0 / guiHeight) * Screenshots.GRID_H) - 1;
		int left = (int) ((150.0 / guiWidth) * Screenshots.GRID_W) - 1;
		int right = Screenshots.GRID_W - (int) ((190.0 / guiWidth) * Screenshots.GRID_W) + 1;
		int bottom = Screenshots.GRID_H - (int) ((110.0 / guiHeight) * Screenshots.GRID_H) + 1;
		long total = 0;
		int counted = 0;
		int worst = 0;
		for (int y = 0; y < Screenshots.GRID_H; y++) {
			for (int x = 0; x < Screenshots.GRID_W; x++) {
				boolean panel = y < top || y >= bottom || x < left || x >= right;
				if (!panel) {
					continue;
				}
				int d = Math.abs(cells[y * Screenshots.GRID_W + x] - expected[y * Screenshots.GRID_W + x]);
				total += d;
				worst = Math.max(worst, d);
				counted++;
			}
		}
		double mean = counted == 0 ? 0 : (double) total / counted;
		System.out.printf(java.util.Locale.ROOT, "%s against its reference: mean difference %.1f, worst %d over %d panel cells%n",
				name, mean, worst, counted);
		if (mean > 8 || worst > 90) {
			throw new AssertionError(String.format(java.util.Locale.ROOT,
					"The editor panels no longer look like the reference (mean difference %.1f, worst %d)", mean, worst));
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
