package io.github.firestormfmd.scenescripter.test;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

/** Helpers for the client game tests' screenshots. */
final class Screenshots {
	private Screenshots() {
	}

	static BufferedImage read(Path png) {
		try {
			return ImageIO.read(png.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static BufferedImage scaled(BufferedImage image, int width, int height) {
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return out;
	}

	static void printThumbnail(Path png, String name) {
		printThumbnail(png, name, 0, 0, 1, 1);
	}

	/**
	 * Prints a small JPEG of part of a screenshot (given as fractions of its width and height) to the log as base64
	 * lines ({@code THUMB <name> <part>}), so it can be looked at from the CI log where artifacts are out of reach.
	 */
	static void printThumbnail(Path png, String name, double left, double top, double right, double bottom) {
		try {
			BufferedImage full = read(png);
			int x0 = (int) (full.getWidth() * left);
			int y0 = (int) (full.getHeight() * top);
			BufferedImage part = full.getSubimage(x0, y0, (int) (full.getWidth() * right) - x0, (int) (full.getHeight() * bottom) - y0);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(scaled(part, 320, 180), "jpg", bytes);
			String text = Base64.getEncoder().encodeToString(bytes.toByteArray());
			for (int i = 0, part = 0; i < text.length(); i += 2000, part++) {
				System.out.println("THUMB " + name + " " + part + " " + text.substring(i, Math.min(text.length(), i + 2000)));
			}
			System.out.println("THUMB " + name + " end");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
