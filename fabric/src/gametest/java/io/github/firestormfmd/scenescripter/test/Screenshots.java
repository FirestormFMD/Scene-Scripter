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

	/** Width and height of the brightness grid screenshots are compared by. */
	static final int GRID_W = 48;
	static final int GRID_H = 27;

	/** Average brightness (0 to 255) of each cell of a {@link #GRID_W} by {@link #GRID_H} grid, row by row. */
	static int[] fingerprint(Path png) {
		BufferedImage image = scaled(read(png), GRID_W * 8, GRID_H * 8);
		int[] cells = new int[GRID_W * GRID_H];
		for (int cy = 0; cy < GRID_H; cy++) {
			for (int cx = 0; cx < GRID_W; cx++) {
				long sum = 0;
				for (int y = 0; y < 8; y++) {
					for (int x = 0; x < 8; x++) {
						int p = image.getRGB(cx * 8 + x, cy * 8 + y);
						sum += ((p >> 16 & 0xFF) * 299 + (p >> 8 & 0xFF) * 587 + (p & 0xFF) * 114) / 1000;
					}
				}
				cells[cy * GRID_W + cx] = (int) (sum / 64);
			}
		}
		return cells;
	}

	static String hex(int[] cells) {
		StringBuilder out = new StringBuilder();
		for (int c : cells) {
			out.append(String.format("%02x", c));
		}
		return out.toString();
	}

	static int[] unhex(String text) {
		String t = text.trim();
		int[] cells = new int[t.length() / 2];
		for (int i = 0; i < cells.length; i++) {
			cells[i] = Integer.parseInt(t.substring(i * 2, i * 2 + 2), 16);
		}
		return cells;
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
			BufferedImage region = full.getSubimage(x0, y0, (int) (full.getWidth() * right) - x0, (int) (full.getHeight() * bottom) - y0);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(scaled(region, 256, 144), "jpg", bytes);
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
