package io.github.firestormfmd.scenescripter.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;
import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * The timeline along the bottom: play controls, a ruler to scrub, and the selected object's motion clips, events
 * and keyframes. Drag keys to move them, right-click a key to change its curve, scroll to zoom.
 */
final class Timeline {
	private static final int HEADER = 18;
	private static final int RULER = 14;
	private static final int LABELS = 110;

	private final EditorScreen screen;
	private int x;
	private int y;
	private int w;
	private int h;
	private boolean scrubbing;
	private String dragChannel;
	private int dragFrom = -1;
	private int dragTo = -1;
	private int lastSeek = -1;

	Timeline(EditorScreen screen) {
		this.screen = screen;
	}

	private int trackX() {
		return x + LABELS;
	}

	private int trackW() {
		return Math.max(10, w - LABELS - 6);
	}

	int tickToX(int tick) {
		double f = (double) (tick - EditorState.viewStart) / Math.max(1, EditorState.viewEnd - EditorState.viewStart);
		return trackX() + (int) Math.round(f * trackW());
	}

	int xToTick(double px) {
		double f = (px - trackX()) / trackW();
		return (int) Math.round(EditorState.viewStart + f * (EditorState.viewEnd - EditorState.viewStart));
	}

	void draw(Scene scene, int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		if (EditorState.viewEnd <= EditorState.viewStart || EditorState.viewEnd > scene.length() * 2) {
			EditorState.viewStart = 0;
			EditorState.viewEnd = scene.length();
		}
		Ui ui = screen.ui();
		ui.panel(x, y, w, h);
		header(scene);

		int rulerY = y + HEADER;
		ui.fill(trackX(), rulerY, trackW(), RULER, Ui.PANEL_DARK);
		int span = EditorState.viewEnd - EditorState.viewStart;
		int step = span > 2400 ? 200 : span > 1200 ? 100 : span > 400 ? 40 : span > 200 ? 20 : 10;
		for (int t = (EditorState.viewStart / step) * step; t <= EditorState.viewEnd; t += step) {
			if (t < EditorState.viewStart) {
				continue;
			}
			int px = tickToX(t);
			ui.fill(px, rulerY + 9, 1, 5, Ui.TEXT_DIM);
			ui.text(String.valueOf(t), px + 2, rulerY + 2, Ui.TEXT_DIM);
		}
		int endX = tickToX(scene.length());
		if (endX < trackX() + trackW()) {
			ui.fill(endX, rulerY, trackX() + trackW() - endX, h - HEADER, 0x60000000);
		}
		ui.area(trackX(), rulerY, trackW(), RULER, (b, mx, my) -> {
			if (b == 0) {
				scrubbing = true;
				seekTo(xToTick(mx));
			}
		});

		int rowY = rulerY + RULER + 2;
		SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
		if (o == null) {
			ui.text("Select an object to see its keyframes", x + 6, rowY + 2, Ui.TEXT_DIM);
		} else {
			rowY = objectRows(o, rowY);
		}

		int head = tickToX(ClientScene.tick());
		if (head >= trackX() && head <= trackX() + trackW()) {
			ui.fill(head, rulerY, 1, h - HEADER - 2, Ui.WARN);
		}
	}

	private void header(Scene scene) {
		Ui ui = screen.ui();
		int bx = x + 4;
		int by = y + 3;
		ui.button(bx, by, 18, 12, "|<", false, () -> ClientNet.seek(0));
		bx += 20;
		ui.button(bx, by, 18, 12, "<", false, () -> ClientNet.seek(Math.max(0, ClientScene.tick() - 1)));
		bx += 20;
		boolean playing = ClientScene.state().playing();
		ui.button(bx, by, 34, 12, playing ? "Pause" : "Play", playing, playing ? ClientNet::pause : ClientNet::play);
		bx += 36;
		ui.button(bx, by, 18, 12, ">", false, () -> ClientNet.seek(ClientScene.tick() + 1));
		bx += 20;
		ui.button(bx, by, 18, 12, ">|", false, () -> ClientNet.seek(scene.length()));
		bx += 24;
		int tick = ClientScene.tick();
		String time = String.format("%d / %d  (%.2fs)", tick, scene.length(), tick / 20.0);
		ui.text(time, bx, by + 2, Ui.TEXT);
		bx += ui.width(time) + 10;
		float speed = ClientScene.state().speed();
		for (float s : new float[] {0.25f, 0.5f, 1f, 2f}) {
			String label = (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "x";
			ui.button(bx, by, 26, 12, label, Math.abs(speed - s) < 0.01, () -> ClientNet.speed(s));
			bx += 28;
		}
		bx += 6;
		boolean looping = ClientScene.state().loopStart() >= 0;
		ui.button(bx, by, 40, 12, "Loop", looping, () -> {
			if (looping) {
				ClientNet.loop(-1, -1);
			} else {
				ClientNet.loop(Math.max(0, EditorState.viewStart), Math.min(scene.length(), EditorState.viewEnd));
			}
		});
		bx += 42;
		ui.button(bx, by, 60, 12, "Length...", false, () -> screen.editText(x + 4, y + 3, 80, String.valueOf(scene.length()), v -> {
			try {
				int len = Math.max(20, Integer.parseInt(v.trim()));
				ClientNet.edit(new io.github.firestormfmd.scenescripter.core.edit.Edits.SetSceneHeader(scene.name(), len,
						scene.origin(), scene.bounds(), scene.settings()));
			} catch (NumberFormatException e) {
				screen.status("Length must be a whole number of ticks");
			}
		}));
	}

	private int objectRows(SceneObject o, int rowY) {
		Ui ui = screen.ui();
		int bottom = y + h - Ui.ROW_HEIGHT;

		// Lifetime bar.
		ui.text("Lifetime", x + 6, rowY + 2, Ui.TEXT_DIM);
		int from = tickToX(o.spawnTick());
		int to = tickToX(o.despawnTick() < 0 ? ClientScene.scene().map(Scene::length).orElse(0) : o.despawnTick());
		ui.fill(Math.max(trackX(), from), rowY + 3, Math.max(0, Math.min(trackX() + trackW(), to) - Math.max(trackX(), from)), 5, 0xFF3E7D52);
		rowY += Ui.ROW_HEIGHT;

		// Motion clips.
		for (MotionClip clip : o.motion()) {
			if (rowY > bottom) {
				return rowY;
			}
			ui.text(ui.fit("Path " + clip.pathId(), LABELS - 8), x + 6, rowY + 2, Ui.TEXT_DIM);
			int cs = tickToX(clip.startTick());
			int ce = clip.timing() == io.github.firestormfmd.scenescripter.core.scene.TimingMode.FIT
					? tickToX(clip.endTick()) : cs + 40;
			ui.fill(cs, rowY + 2, Math.max(4, ce - cs), 8, 0xFF3B5B9A);
			rowY += Ui.ROW_HEIGHT;
		}

		// Events.
		if (!o.events().isEmpty() && rowY <= bottom) {
			ui.text("Events", x + 6, rowY + 2, Ui.TEXT_DIM);
			for (SceneEvent e : o.events()) {
				int ex = tickToX(e.tick());
				if (ex >= trackX() && ex <= trackX() + trackW()) {
					ui.fill(ex - 1, rowY + 1, 3, 10, e.isGenerated() ? 0xFFB06CFF : 0xFFFF8A3D);
				}
			}
			rowY += Ui.ROW_HEIGHT;
		}

		// Keyframed channels.
		List<Map.Entry<String, Channel<?>>> rows = new ArrayList<>();
		for (Map.Entry<String, Channel<?>> e : o.channels().entrySet()) {
			if (!e.getValue().isEmpty()) {
				rows.add(e);
			}
		}
		for (Map.Entry<String, Channel<?>> row : rows) {
			if (rowY > bottom) {
				break;
			}
			String name = row.getKey();
			boolean rowSelected = name.equals(EditorState.selectedChannel);
			ui.text(ui.fit(name, LABELS - 8), x + 6, rowY + 2, rowSelected ? Ui.KEY : Ui.TEXT);
			for (Keyframe<?> k : row.getValue().keys()) {
				int kt = name.equals(dragChannel) && k.tick() == dragFrom && dragTo >= 0 ? dragTo : k.tick();
				int kx = tickToX(kt);
				if (kx < trackX() - 3 || kx > trackX() + trackW() + 3) {
					continue;
				}
				boolean selected = rowSelected && k.tick() == EditorState.selectedKeyTick;
				int color = k.isGenerated() ? 0xFFB06CFF : selected ? 0xFFFFFFFF : Ui.KEY;
				ui.diamond(kx, rowY + 5, 3, color, true);
				int keyTick = k.tick();
				final int ry = rowY;
				ui.area(kx - 4, ry, 9, Ui.ROW_HEIGHT, (b, mx, my) -> {
					EditorState.selectedChannel = name;
					EditorState.selectedKeyTick = keyTick;
					if (b == 1) {
						EditActions.cycleInterpolation(o, name, keyTick);
						screen.status("Curve: " + nextInterpolationName(o, name, keyTick));
					} else {
						dragChannel = name;
						dragFrom = keyTick;
						dragTo = -1;
					}
				});
			}
			rowY += Ui.ROW_HEIGHT;
		}
		return rowY;
	}

	private static String nextInterpolationName(SceneObject o, String channel, int tick) {
		return o.channel(channel).flatMap(ch -> ch.keyAt(tick))
				.map(k -> io.github.firestormfmd.scenescripter.core.anim.Interpolation.values()[
						(k.interpolation().ordinal() + 1) % io.github.firestormfmd.scenescripter.core.anim.Interpolation.values().length].id())
				.orElse("");
	}

	private void seekTo(int tick) {
		int t = Math.max(0, tick);
		if (t != lastSeek) {
			lastSeek = t;
			ClientNet.seek(t);
		}
	}

	boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	boolean mouseDragged(double mx, double my, int button) {
		if (scrubbing) {
			seekTo(xToTick(mx));
			return true;
		}
		if (dragChannel != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragTo = Math.max(0, xToTick(mx));
			return true;
		}
		return false;
	}

	boolean mouseReleased(double mx, double my, int button) {
		if (scrubbing) {
			scrubbing = false;
			lastSeek = -1;
			return true;
		}
		if (dragChannel != null) {
			if (dragTo >= 0 && dragTo != dragFrom) {
				String channel = dragChannel;
				int from = dragFrom;
				int to = dragTo;
				ClientScene.object(EditorState.selectedObject).ifPresent(o -> EditActions.moveKey(o, channel, from, to));
				EditorState.selectedKeyTick = to;
			}
			dragChannel = null;
			dragFrom = -1;
			dragTo = -1;
			return true;
		}
		return false;
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (my < y || my > y + h) {
			return false;
		}
		int span = EditorState.viewEnd - EditorState.viewStart;
		int center = xToTick(mx);
		double factor = amount > 0 ? 0.8 : 1.25;
		int newSpan = (int) Math.clamp(Math.round(span * factor), 20, 20 * 60 * 60);
		double f = (double) (center - EditorState.viewStart) / Math.max(1, span);
		EditorState.viewStart = Math.max(0, (int) Math.round(center - f * newSpan));
		EditorState.viewEnd = EditorState.viewStart + newSpan;
		return true;
	}
}
