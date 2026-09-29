package io.github.firestormfmd.scenescripter.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

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
	/** Right-dragging on the ruler marks the work range, which Loop plays and capture records. */
	private boolean ranging;
	private int rangeFrom;
	private int rangeTo;
	private String dragChannel;
	private int dragFrom = -1;
	private int dragTo = -1;
	private int lastSeek = -1;
	/** Box selection of keys, in screen coordinates. */
	private boolean boxing;
	private double boxX0;
	private double boxY0;
	private double boxX1;
	private double boxY1;
	private record KeyPos(String channel, int tick, int x, int y) {
	}
	private final List<KeyPos> keyPositions = new ArrayList<>();
	/** Curve editing: the key or handle being dragged ({@code 0} key, {@code -1} in handle, {@code 1} out handle). */
	private String curveChannel;
	private int curveKey = -1;
	private int curveHandle;
	private double curveDragTick;
	private double curveDragValue;
	private boolean curveDragging;
	private double graphMin;
	private double graphMax;
	private int graphY;
	private int graphH;

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
		if (EditorState.rulerSeconds) {
			// Whole or half seconds, so the labels stay round.
			step = Math.max(10, (step + 19) / 20 * 20);
		}
		var st = ClientScene.state();
		int rangeStart = ranging ? Math.min(rangeFrom, rangeTo) : st.loopStart();
		int rangeEnd = ranging ? Math.max(rangeFrom, rangeTo) : st.loopEnd();
		if (rangeStart >= 0 && rangeEnd > rangeStart) {
			int from = Math.max(trackX(), tickToX(rangeStart));
			int to = Math.min(trackX() + trackW(), tickToX(rangeEnd));
			if (to > from) {
				ui.fill(from, rulerY, to - from, RULER, 0x604C8DFF);
			}
		}
		for (int t = (EditorState.viewStart / step) * step; t <= EditorState.viewEnd; t += step) {
			if (t < EditorState.viewStart) {
				continue;
			}
			int px = tickToX(t);
			ui.fill(px, rulerY + 9, 1, 5, Ui.TEXT_DIM);
			String label = EditorState.rulerSeconds
					? (t % 20 == 0 ? (t / 20) + "s" : String.format(java.util.Locale.ROOT, "%.1fs", t / 20.0)) : String.valueOf(t);
			ui.text(label, px + 2, rulerY + 2, Ui.TEXT_DIM);
		}
		int endX = tickToX(scene.length());
		if (endX < trackX() + trackW()) {
			ui.fill(endX, rulerY, trackX() + trackW() - endX, h - HEADER, 0x60000000);
		}
		ui.area(trackX(), rulerY, trackW(), RULER, (b, mx, my) -> {
			if (b == 0) {
				scrubbing = true;
				seekTo(xToTick(mx));
			} else if (b == 1) {
				ranging = true;
				rangeFrom = Math.max(0, xToTick(mx));
				rangeTo = rangeFrom;
			}
		});

		int rowY = rulerY + RULER + 2;
		SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
		if (o == null) {
			ui.text("Select an object to see its keyframes", x + 6, rowY + 2, Ui.TEXT_DIM);
		} else if (EditorState.curveMode && curveChannel(o) != null) {
			curves(o, curveChannel(o), rowY);
		} else {
			rowY = objectRows(o, rowY);
		}

		if (boxing) {
			int bx0 = (int) Math.min(boxX0, boxX1);
			int by0 = (int) Math.min(boxY0, boxY1);
			ui.fill(bx0, by0, (int) Math.abs(boxX1 - boxX0), (int) Math.abs(boxY1 - boxY0), 0x406EC8FF);
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
			// Loop uses the work range (right-drag on the ruler), or the visible part of the timeline.
			if (looping) {
				ClientNet.loop(-1, -1);
			} else {
				ClientNet.loop(Math.max(0, EditorState.viewStart), Math.min(scene.length(), EditorState.viewEnd));
			}
		});
		bx += 42;
		ui.button(bx, by, 44, 12, "Curves", EditorState.curveMode, () -> {
			EditorState.curveMode = !EditorState.curveMode;
			if (EditorState.curveMode && EditorState.selectedChannel == null) {
				screen.status("Click a number channel's name, then Curves shows it as a graph");
			}
		});
		bx += 46;
		ui.button(bx, by, 26, 12, EditorState.rulerSeconds ? "sec" : "tick", false,
				() -> EditorState.rulerSeconds = !EditorState.rulerSeconds);
		bx += 28;
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

		// Keyframed channels. Dragging on empty track space draws a box that selects the keys inside it.
		keyPositions.clear();
		int rowsTop = rowY;
		ui.area(trackX(), rowsTop, trackW(), Math.max(0, bottom + Ui.ROW_HEIGHT - rowsTop), (b, mx, my) -> {
			if (b == 0) {
				boxing = true;
				boxX0 = boxX1 = mx;
				boxY0 = boxY1 = my;
				EditorState.selectedKeys.clear();
			}
		});
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
			ui.area(x + 2, rowY, LABELS - 4, Ui.ROW_HEIGHT, (b, mx, my) -> EditorState.selectedChannel = name);
			for (Keyframe<?> k : row.getValue().keys()) {
				var ref = new EditorState.KeyRef(name, k.tick());
				boolean inSelection = EditorState.selectedKeys.contains(ref);
				boolean moving = dragChannel != null && dragTo >= 0 && (name.equals(dragChannel) && k.tick() == dragFrom
						|| inSelection && EditorState.selectedKeys.contains(new EditorState.KeyRef(dragChannel, dragFrom)));
				int kt = moving ? k.tick() + dragTo - dragFrom : k.tick();
				int kx = tickToX(kt);
				if (kx < trackX() - 3 || kx > trackX() + trackW() + 3) {
					continue;
				}
				keyPositions.add(new KeyPos(name, k.tick(), kx, rowY + 5));
				boolean selected = inSelection || rowSelected && k.tick() == EditorState.selectedKeyTick;
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
						var clicked = new EditorState.KeyRef(name, keyTick);
						if (!EditorState.selectedKeys.contains(clicked)) {
							EditorState.selectedKeys.clear();
							EditorState.selectedKeys.add(clicked);
						}
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

	// ---- Curve editor ----

	/** The selected channel if it holds a single number, which is what the curve editor can show. */
	private static Channel<?> curveChannel(SceneObject o) {
		if (EditorState.selectedChannel == null) {
			return null;
		}
		Channel<?> ch = o.channel(EditorState.selectedChannel).orElse(null);
		if (ch == null || !ch.type().interpolates() || ch.type().components() != 1 || ch.isEmpty()) {
			return null;
		}
		return ch;
	}

	private static double number(Object v) {
		return v instanceof Number n ? n.doubleValue() : 0;
	}

	private int valueToY(double v) {
		return graphY + graphH - (int) Math.round((v - graphMin) / (graphMax - graphMin) * graphH);
	}

	private double yToValue(double py) {
		return graphMin + (graphY + graphH - py) / graphH * (graphMax - graphMin);
	}

	private double xToTickExact(double px) {
		double f = (px - trackX()) / trackW();
		return EditorState.viewStart + f * (EditorState.viewEnd - EditorState.viewStart);
	}

	/**
	 * Draws the channel as a curve. Drag keys to change their time and value; Bezier keys show handles to drag;
	 * right-click a key to change its curve type.
	 */
	private void curves(SceneObject o, Channel<?> ch, int top) {
		Ui ui = screen.ui();
		String name = EditorState.selectedChannel;
		ui.text(ui.fit(name, LABELS - 8), x + 6, top + 2, Ui.KEY);
		ui.text("drag keys", x + 6, top + 14, Ui.TEXT_DIM);
		ui.text("and handles", x + 6, top + 24, Ui.TEXT_DIM);
		graphY = top + 2;
		graphH = Math.max(20, y + h - graphY - 6);
		if (!curveDragging) {
			double min = Double.MAX_VALUE;
			double max = -Double.MAX_VALUE;
			int step = Math.max(1, (EditorState.viewEnd - EditorState.viewStart) / 200);
			for (int t = EditorState.viewStart; t <= EditorState.viewEnd; t += step) {
				double v = number(ch.valueAt(t));
				min = Math.min(min, v);
				max = Math.max(max, v);
			}
			for (Keyframe<?> k : ch.keys()) {
				double v = number(k.value());
				min = Math.min(min, v);
				max = Math.max(max, v);
			}
			if (max - min < 1.0e-6) {
				min -= 1;
				max += 1;
			}
			double pad = (max - min) * 0.1;
			graphMin = min - pad;
			graphMax = max + pad;
		}
		ui.fill(trackX(), graphY, trackW(), graphH, Ui.PANEL_DARK);
		for (int i = 0; i <= 4; i++) {
			double v = graphMin + (graphMax - graphMin) * i / 4;
			int gy = valueToY(v);
			ui.fill(trackX(), gy, trackW(), 1, 0x30FFFFFF);
			ui.text(String.format(java.util.Locale.ROOT, "%.1f", v), trackX() + 2, gy - 9, Ui.TEXT_DIM);
		}

		// The curve, one column at a time.
		int prevY = Integer.MIN_VALUE;
		for (int px = 0; px < trackW(); px++) {
			double t = xToTickExact(trackX() + px);
			int cy = Math.clamp(valueToY(number(ch.valueAt(t))), graphY, graphY + graphH);
			int y0 = prevY == Integer.MIN_VALUE ? cy : Math.min(prevY, cy);
			int y1 = prevY == Integer.MIN_VALUE ? cy : Math.max(prevY, cy);
			ui.fill(trackX() + px, y0, 1, Math.max(1, y1 - y0 + 1), 0xFF6EC8FF);
			prevY = cy;
		}

		List<? extends Keyframe<?>> keys = ch.keys();
		for (int i = 0; i < keys.size(); i++) {
			Keyframe<?> k = keys.get(i);
			boolean dragged = curveDragging && name.equals(curveChannel) && k.tick() == curveKey;
			double kt = dragged && curveHandle == 0 ? curveDragTick : k.tick();
			double kv = dragged && curveHandle == 0 ? curveDragValue : number(k.value());
			int kx = tickToX((int) Math.round(kt));
			int ky = valueToY(kv);
			if (kx < trackX() - 4 || kx > trackX() + trackW() + 4) {
				continue;
			}
			if (k.interpolation() == io.github.firestormfmd.scenescripter.core.anim.Interpolation.BEZIER || k.handles() != null) {
				io.github.firestormfmd.scenescripter.core.anim.Handles hd = handlesOf(keys, i);
				for (int side : new int[] {-1, 1}) {
					double dt = side < 0 ? hd.inDt() : hd.outDt();
					double dv = side < 0 ? hd.inDv() : hd.outDv();
					if (dragged && curveHandle == side) {
						dt = curveDragTick - k.tick();
						dv = curveDragValue - number(k.value());
					}
					int hx = tickToX((int) Math.round(k.tick() + dt));
					int hy = valueToY(number(k.value()) + dv);
					line(ui, kx, ky, hx, hy, 0x90FFFFFF);
					ui.fill(hx - 2, hy - 2, 5, 5, 0xFFFFFFFF);
					final int s = side;
					final int keyTick = k.tick();
					ui.area(hx - 3, hy - 3, 7, 7, (b, mx, my) -> startCurveDrag(name, keyTick, s, mx, my));
				}
			}
			ui.diamond(kx, ky, 3, k.isGenerated() ? 0xFFB06CFF : Ui.KEY, true);
			final int keyTick = k.tick();
			ui.area(kx - 4, ky - 4, 9, 9, (b, mx, my) -> {
				EditorState.selectedKeyTick = keyTick;
				if (b == 1) {
					EditActions.cycleInterpolation(o, name, keyTick);
					screen.status("Curve: " + nextInterpolationName(o, name, keyTick));
				} else {
					startCurveDrag(name, keyTick, 0, mx, my);
				}
			});
		}
	}

	/** A key's handles: its own, or flat ones a third of the way to its neighbours. */
	private static io.github.firestormfmd.scenescripter.core.anim.Handles handlesOf(List<? extends Keyframe<?>> keys, int i) {
		Keyframe<?> k = keys.get(i);
		if (k.handles() != null) {
			return k.handles();
		}
		double before = i > 0 ? (k.tick() - keys.get(i - 1).tick()) / 3.0 : 5;
		double after = i + 1 < keys.size() ? (keys.get(i + 1).tick() - k.tick()) / 3.0 : 5;
		return new io.github.firestormfmd.scenescripter.core.anim.Handles(-before, 0, after, 0);
	}

	private void startCurveDrag(String channel, int keyTick, int handle, double mx, double my) {
		curveChannel = channel;
		curveKey = keyTick;
		curveHandle = handle;
		curveDragTick = xToTickExact(mx);
		curveDragValue = yToValue(my);
		curveDragging = true;
		EditorState.selectedKeyTick = keyTick;
	}

	private static void line(Ui ui, int x0, int y0, int x1, int y1, int color) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= steps; i++) {
			double f = steps == 0 ? 0 : (double) i / steps;
			ui.fill((int) Math.round(x0 + (x1 - x0) * f), (int) Math.round(y0 + (y1 - y0) * f), 1, 1, color);
		}
	}

	/** Writes the dragged key or handle back as an edit. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void finishCurveDrag() {
		curveDragging = false;
		SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
		Channel ch = o == null ? null : o.channel(curveChannel).orElse(null);
		if (ch == null) {
			return;
		}
		List<Keyframe<?>> keys = ch.keys();
		int index = -1;
		for (int i = 0; i < keys.size(); i++) {
			if (keys.get(i).tick() == curveKey) {
				index = i;
			}
		}
		if (index < 0) {
			return;
		}
		Keyframe<?> k = keys.get(index);
		Object value;
		int tick = k.tick();
		io.github.firestormfmd.scenescripter.core.anim.Handles handles = k.handles();
		io.github.firestormfmd.scenescripter.core.anim.Interpolation interp = k.interpolation();
		if (curveHandle == 0) {
			tick = Math.max(0, (int) Math.round(curveDragTick));
			value = ch.type() == io.github.firestormfmd.scenescripter.core.anim.ValueType.INT
					? (Object) (int) Math.round(curveDragValue) : (Object) curveDragValue;
		} else {
			value = k.value();
			var hd = handlesOf(keys, index);
			double dt = curveDragTick - k.tick();
			double dv = curveDragValue - number(k.value());
			handles = curveHandle < 0
					? new io.github.firestormfmd.scenescripter.core.anim.Handles(Math.min(-0.5, dt), dv, hd.outDt(), hd.outDv())
					: new io.github.firestormfmd.scenescripter.core.anim.Handles(hd.inDt(), hd.inDv(), Math.max(0.5, dt), dv);
			interp = io.github.firestormfmd.scenescripter.core.anim.Interpolation.BEZIER;
		}
		java.util.List<io.github.firestormfmd.scenescripter.core.edit.EditOp> ops = new java.util.ArrayList<>();
		if (tick != k.tick()) {
			ops.add(new io.github.firestormfmd.scenescripter.core.edit.Edits.RemoveKeyframe(o.id(), curveChannel, k.tick()));
		}
		ops.add(new io.github.firestormfmd.scenescripter.core.edit.Edits.SetKeyframe(o.id(), curveChannel,
				new Keyframe<>(tick, value, interp, handles, null)));
		ClientNet.edit(new io.github.firestormfmd.scenescripter.core.edit.Edits.Composite("Edit curve", ops));
		EditorState.selectedKeyTick = tick;
	}

	private static String nextInterpolationName(SceneObject o, String channel, int tick) {
		return o.channel(channel).flatMap(ch -> ch.keyAt(tick))
				.map(k -> io.github.firestormfmd.scenescripter.core.anim.Interpolation.values()[
						(k.interpolation().ordinal() + 1) % io.github.firestormfmd.scenescripter.core.anim.Interpolation.values().length].id())
				.orElse("");
	}

	/** Moves several keys by the same number of ticks: all are lifted first so none lands on another's old place. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void moveKeys(SceneObject o, List<EditorState.KeyRef> keys, int delta) {
		List<Object[]> lifted = new ArrayList<>();
		for (var ref : keys) {
			o.channel(ref.channel()).ifPresent(ch -> ch.remove(ref.tick()).ifPresent(k -> lifted.add(new Object[] {ch, k})));
		}
		for (Object[] pair : lifted) {
			Channel ch = (Channel) pair[0];
			Keyframe<?> k = (Keyframe<?>) pair[1];
			ch.putUnchecked(new Keyframe<>(Math.max(0, k.tick() + delta), k.value(), k.interpolation(), k.handles(), null));
		}
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
		if (boxing) {
			boxX1 = mx;
			boxY1 = my;
			return true;
		}
		if (curveDragging && button == InputConstants.MOUSE_BUTTON_LEFT) {
			curveDragTick = xToTickExact(mx);
			curveDragValue = yToValue(my);
			return true;
		}
		if (scrubbing) {
			seekTo(xToTick(mx));
			return true;
		}
		if (ranging) {
			rangeTo = Math.max(0, xToTick(mx));
			return true;
		}
		if (dragChannel != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
			dragTo = Math.max(0, xToTick(mx));
			return true;
		}
		return false;
	}

	boolean mouseReleased(double mx, double my, int button) {
		if (boxing) {
			boxing = false;
			double x0 = Math.min(boxX0, boxX1);
			double x1 = Math.max(boxX0, boxX1);
			double y0 = Math.min(boxY0, boxY1);
			double y1 = Math.max(boxY0, boxY1);
			for (KeyPos k : keyPositions) {
				if (k.x() >= x0 && k.x() <= x1 && k.y() >= y0 - 4 && k.y() <= y1 + 4) {
					EditorState.selectedKeys.add(new EditorState.KeyRef(k.channel(), k.tick()));
				}
			}
			if (!EditorState.selectedKeys.isEmpty()) {
				screen.status(EditorState.selectedKeys.size() + " keys selected: drag to move, Ctrl+C to copy, Delete to remove");
			}
			return true;
		}
		if (curveDragging) {
			finishCurveDrag();
			return true;
		}
		if (scrubbing) {
			scrubbing = false;
			lastSeek = -1;
			return true;
		}
		if (ranging) {
			ranging = false;
			int from = Math.min(rangeFrom, rangeTo);
			int to = Math.min(ClientScene.scene().map(Scene::length).orElse(Integer.MAX_VALUE), Math.max(rangeFrom, rangeTo));
			if (to - from >= 2) {
				ClientNet.loop(from, to);
				screen.status("Work range " + from + " to " + to + ": Loop plays it, capture records it, Home and End jump to its ends");
			} else {
				ClientNet.loop(-1, -1);
				screen.status("Work range cleared");
			}
			return true;
		}
		if (dragChannel != null) {
			if (dragTo >= 0 && dragTo != dragFrom) {
				String channel = dragChannel;
				int from = dragFrom;
				int to = dragTo;
				var grabbed = new EditorState.KeyRef(channel, from);
				if (EditorState.selectedKeys.size() > 1 && EditorState.selectedKeys.contains(grabbed)) {
					int delta = to - from;
					var moving = List.copyOf(EditorState.selectedKeys);
					ClientScene.object(EditorState.selectedObject).ifPresent(o -> EditActions.change(o,
							"Move " + moving.size() + " keys", c -> moveKeys(c, moving, delta)));
					EditorState.selectedKeys.clear();
					moving.forEach(k -> EditorState.selectedKeys.add(new EditorState.KeyRef(k.channel(), Math.max(0, k.tick() + delta))));
				} else {
					ClientScene.object(EditorState.selectedObject).ifPresent(o -> EditActions.moveKey(o, channel, from, to));
					EditorState.selectedKeys.clear();
					EditorState.selectedKeys.add(new EditorState.KeyRef(channel, to));
				}
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
