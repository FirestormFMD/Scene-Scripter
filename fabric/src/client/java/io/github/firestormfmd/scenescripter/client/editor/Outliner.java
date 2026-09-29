package io.github.firestormfmd.scenescripter.client.editor;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * The list of objects and paths on the left.
 */
final class Outliner {
	private final EditorScreen screen;
	private int scroll;
	/** The hidden list last sent to the server; null forces a resend, such as after reopening the editor. */
	private java.util.List<String> sentView;

	Outliner(EditorScreen screen) {
		this.screen = screen;
	}

	void draw(Scene scene, int x, int y, int w, int h) {
		Ui ui = screen.ui();
		ui.panel(x, y, w, h);
		ui.text("Objects", x + 6, y + 5, Ui.TEXT_DIM);
		ui.button(x + w - 42, y + 3, 38, 12, "+ Add", false, () -> {
			screen.setTool(EditorState.Tool.PLACE);
			EditorState.paletteOpen = true;
		});
		int rowY = y + 18;
		int bottom = y + h - 70;
		boolean trackSelected = Scene.TRACKS_ID.equals(EditorState.selectedObject);
		if (trackSelected) {
			ui.fill(x + 2, rowY - 1, w - 4, Ui.ROW_HEIGHT, Ui.ACCENT_DIM);
		}
		ui.text("Scene track", x + 6, rowY + 1, Ui.KEY);
		ui.text("time, sky", x + w - 54, rowY + 1, Ui.TEXT_DIM);
		ui.area(x + 2, rowY - 1, w - 4, Ui.ROW_HEIGHT, (b, mx, my) -> EditorState.selectObject(Scene.TRACKS_ID));
		rowY += Ui.ROW_HEIGHT;
		int index = 0;
		for (SceneObject o : scene.objects()) {
			if (index++ < scroll) {
				continue;
			}
			if (rowY > bottom) {
				break;
			}
			boolean selected = o.id().equals(EditorState.selectedObject);
			boolean exists = o.existsAt(ClientScene.tick());
			if (selected) {
				ui.fill(x + 2, rowY - 1, w - 4, Ui.ROW_HEIGHT, Ui.ACCENT_DIM);
			}
			boolean hiddenHere = EditorState.hidden.contains(o.id());
			String label = ui.fit(o.name(), w - 48);
			ui.text(label, x + 6, rowY + 1, exists && !hiddenHere ? Ui.TEXT : Ui.TEXT_DIM);
			ui.area(x + 2, rowY - 1, w - 4, Ui.ROW_HEIGHT, (b, mx, my) -> EditorState.selectObject(o.id()));
			toggle(ui, x + w - 38, rowY, "V", !hiddenHere, "hide", () -> {
				if (!EditorState.hidden.remove(o.id())) {
					EditorState.hidden.add(o.id());
				}
			});
			toggle(ui, x + w - 26, rowY, "L", EditorState.locked.contains(o.id()), "lock", () -> {
				if (!EditorState.locked.remove(o.id())) {
					EditorState.locked.add(o.id());
				}
			});
			toggle(ui, x + w - 14, rowY, "S", o.id().equals(EditorState.solo), "solo",
					() -> EditorState.solo = o.id().equals(EditorState.solo) ? null : o.id());
			rowY += Ui.ROW_HEIGHT;
		}
		java.util.List<String> view = EditorState.effectiveHidden(scene);
		if (!view.equals(sentView)) {
			sentView = view;
			ClientNet.editorView(view);
		}
		if (scene.objects().isEmpty()) {
			ui.text("Nothing yet: + Add", x + 6, rowY + 1, Ui.TEXT_DIM);
		}

		int py = y + h - 66;
		ui.fill(x + 2, py - 3, w - 4, 1, Ui.BORDER);
		ui.text("Paths", x + 6, py, Ui.TEXT_DIM);
		ui.button(x + w - 42, py - 2, 38, 12, "+ Draw", false, () -> screen.setTool(EditorState.Tool.PATH));
		int pathY = py + 13;
		for (MotionPath p : scene.paths()) {
			if (pathY > y + h - 12) {
				break;
			}
			boolean selected = p.id().equals(EditorState.selectedPath);
			if (selected) {
				ui.fill(x + 2, pathY - 1, w - 4, Ui.ROW_HEIGHT, Ui.ACCENT_DIM);
			}
			ui.text(ui.fit(p.name(), w - 50), x + 6, pathY + 1, Ui.TEXT);
			ui.text(p.kind().id(), x + w - 40, pathY + 1, Ui.TEXT_DIM);
			ui.area(x + 2, pathY - 1, w - 4, Ui.ROW_HEIGHT, (b, mx, my) -> EditorState.selectPath(p.id()));
			pathY += Ui.ROW_HEIGHT;
		}
	}

	/** A tiny on/off button: visibility, lock or solo. */
	private static void toggle(Ui ui, int x, int y, String label, boolean on, String what, Runnable flip) {
		ui.fill(x, y, 10, 10, on ? Ui.ACCENT_DIM : 0x40000000);
		ui.text(label, x + 2, y + 1, on ? Ui.TEXT : Ui.TEXT_DIM);
		ui.area(x, y, 10, 10, (b, mx, my) -> flip.run());
	}
}
