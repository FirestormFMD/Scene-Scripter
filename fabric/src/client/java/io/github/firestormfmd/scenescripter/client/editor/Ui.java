package io.github.firestormfmd.scenescripter.client.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A tiny immediate-mode UI on top of vanilla GUI drawing. Panels draw themselves every frame and register
 * clickable areas as they go; clicks are matched against the areas from the last frame.
 */
public final class Ui {
	public static final int PANEL = 0xE0181A20;
	public static final int PANEL_DARK = 0xF0101116;
	public static final int BORDER = 0xFF30343E;
	public static final int TEXT = 0xFFE6E8EE;
	public static final int TEXT_DIM = 0xFF9097A6;
	public static final int ACCENT = 0xFF4C8DFF;
	public static final int ACCENT_DIM = 0xFF2A4A80;
	public static final int WARN = 0xFFFF5A5A;
	public static final int GOOD = 0xFF5ADB7A;
	public static final int KEY = 0xFFF2C94C;
	public static final int ROW_HEIGHT = 12;

	/** A clickable area. {@code button} is 0 for left, 1 for right. */
	public record Hit(int x0, int y0, int x1, int y1, ClickAction action) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	@FunctionalInterface
	public interface ClickAction {
		void click(int button, double x, double y);
	}

	private GuiGraphicsExtractor g;
	private Font font;
	private int mouseX;
	private int mouseY;
	private List<Hit> building = new ArrayList<>();
	private List<Hit> lastFrame = new ArrayList<>();

	public void begin(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		this.g = graphics;
		this.font = font;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		building = new ArrayList<>();
	}

	public void end() {
		lastFrame = building;
	}

	public GuiGraphicsExtractor graphics() {
		return g;
	}

	public Font font() {
		return font;
	}

	public int mouseX() {
		return mouseX;
	}

	public int mouseY() {
		return mouseY;
	}

	public boolean hovered(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** Offers a click to the areas registered last frame, topmost (last registered) first. */
	public boolean click(double x, double y, int button) {
		for (int i = lastFrame.size() - 1; i >= 0; i--) {
			Hit h = lastFrame.get(i);
			if (h.contains(x, y)) {
				h.action().click(button, x, y);
				return true;
			}
		}
		return false;
	}

	/** Whether a point is over any UI registered last frame. */
	public boolean over(double x, double y) {
		for (Hit h : lastFrame) {
			if (h.contains(x, y)) {
				return true;
			}
		}
		return false;
	}

	public void area(int x, int y, int w, int h, ClickAction action) {
		building.add(new Hit(x, y, x + w, y + h, action));
	}

	/** Blocks clicks from reaching whatever is behind a panel. */
	public void blocker(int x, int y, int w, int h) {
		area(x, y, w, h, (b, mx, my) -> {
		});
	}

	public void fill(int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + h, color);
	}

	public void panel(int x, int y, int w, int h) {
		fill(x, y, w, h, PANEL);
		g.outline(x, y, w, h, BORDER);
		blocker(x, y, w, h);
	}

	public void text(String s, int x, int y, int color) {
		g.text(font, s, x, y, color, false);
	}

	public int width(String s) {
		return font.width(s);
	}

	/** Text cut to fit a width, with an ellipsis. */
	public String fit(String s, int maxWidth) {
		if (font.width(s) <= maxWidth) {
			return s;
		}
		String dots = "…";
		int w = maxWidth - font.width(dots);
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (font.width(b.toString() + c) > w) {
				break;
			}
			b.append(c);
		}
		return b + dots;
	}

	public boolean button(int x, int y, int w, int h, String label, boolean active, Runnable onClick) {
		boolean hover = hovered(x, y, w, h);
		fill(x, y, w, h, active ? ACCENT_DIM : hover ? 0xFF2C313C : 0xFF22262E);
		g.outline(x, y, w, h, active ? ACCENT : BORDER);
		String t = fit(label, w - 4);
		text(t, x + (w - width(t)) / 2, y + (h - 8) / 2, TEXT);
		area(x, y, w, h, (b, mx, my) -> {
			if (b == 0) {
				onClick.run();
			}
		});
		return hover;
	}

	/** A small diamond marking a keyframe, filled when there is a key at the current tick. */
	public void diamond(int cx, int cy, int r, int color, boolean filled) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			if (filled) {
				g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
			} else {
				g.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 1, color);
				g.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, color);
			}
		}
	}
}
