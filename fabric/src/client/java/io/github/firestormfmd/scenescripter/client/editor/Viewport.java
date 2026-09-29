package io.github.firestormfmd.scenescripter.client.editor;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;
import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * The world view between the panels: selecting, dragging, placing and drawing paths with the mouse.
 */
final class Viewport {
	/** Air path points are placed this far above the ground under the mouse. */
	private static final double AIR_HEIGHT = 4;
	private final EditorScreen screen;
	private boolean draggingActor;
	/** Freehand path sketching: the raw points and where they start in the draft. */
	private final List<Vec3> sketch = new ArrayList<>();
	private int sketchStart = -1;
	private double lastMouseX;
	private double lastMouseY;

	Viewport(EditorScreen screen) {
		this.screen = screen;
	}

	void tick() {
		Scene scene = ClientScene.scene().orElse(null);
		if (scene == null || screen.camera().isLooking()) {
			EditorState.hoveredObject = null;
			return;
		}
		if (screen.inViewport(lastMouseX, lastMouseY)) {
			EditorState.hoveredObject = Picking.pickActor(Picking.ray(lastMouseX, lastMouseY, screen.width, screen.height))
					.orElse(null);
		} else {
			EditorState.hoveredObject = null;
		}
	}

	void drawOverlay(Scene scene) {
		Ui ui = screen.ui();
		lastMouseX = ui.mouseX();
		lastMouseY = ui.mouseY();
		String hint = switch (EditorState.tool) {
			case SELECT -> (EditorState.selectedObject != null ? "Drag the actor to move it; I keys its position" : "Click an actor to select it")
					+ (EditorState.snap == EditorState.Snap.OFF ? "  (G: snap)" : "  (snap: " + EditorState.snap.name().toLowerCase(java.util.Locale.ROOT) + ")");
			case PLACE -> "Click the ground to place: " + EditActions.prettyName(EditorState.placeType);
			case PATH -> (EditorState.pathDraftAir ? "Air path" : "Ground path") + ": " + EditorState.pathDraft.size()
					+ " points. Click to add, Enter to finish, Tab switches ground/air, Esc cancels";
			case BLOCKS -> "Click a block: " + switch (EditorState.blockAction) {
				case BREAK -> "break it";
				case PLACE -> "place " + EditorState.blockState + " on it";
				case USE -> "use it (doors, levers, buttons)";
			} + ".  1 break  2 place  3 use  B set block";
		};
		int hx = screen.viewportX() + 6;
		int hy = screen.viewportY() + screen.viewportHeight() - 14;
		ui.fill(hx - 3, hy - 3, ui.width(hint) + 6, 13, 0xA0000000);
		ui.text(hint, hx, hy, Ui.TEXT);
	}

	boolean mouseClicked(double x, double y, int button) {
		lastMouseX = x;
		lastMouseY = y;
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			screen.startLooking();
			return true;
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		Scene scene = ClientScene.scene().orElse(null);
		if (scene == null) {
			return false;
		}
		Picking.Ray ray = Picking.ray(x, y, screen.width, screen.height);
		switch (EditorState.tool) {
			case SELECT -> {
				String picked = Picking.pickActor(ray).filter(id -> !EditorState.locked.contains(id)).orElse(null);
				if (picked != null) {
					boolean already = picked.equals(EditorState.selectedObject);
					EditorState.selectObject(picked);
					draggingActor = already;
					return true;
				}
				int handle = pickHandle(scene, ray);
				if (handle != 0) {
					draggingActor = false;
					EditorState.dragHandle = handle;
					return true;
				}
				int point = pickPathPoint(scene, ray);
				if (point >= 0) {
					draggingActor = false;
					EditorState.dragPoint = point;
					EditorState.selectedPoint = point;
					return true;
				}
				EditorState.selectObject(null);
				EditorState.selectPath(null);
			}
			case PLACE -> {
				Vec3 at = snapped(screen.groundUnderMouse(x, y));
				if (at != null) {
					EditActions.addObject(EditorState.placeType, at);
					screen.status("Placed " + EditActions.prettyName(EditorState.placeType));
				}
			}
			case PATH -> {
				Vec3 at = snapped(screen.groundUnderMouse(x, y));
				if (at != null) {
					EditorState.pathDraft.add(EditorState.pathDraftAir ? at.add(0, AIR_HEIGHT, 0) : at);
					sketch.clear();
					sketch.add(at);
					sketchStart = EditorState.pathDraft.size() - 1;
				}
			}
			case BLOCKS -> blockClick(ray);
		}
		return true;
	}

	private static Vec3 snapped(Vec3 at) {
		return at == null ? null : EditorState.snap.apply(at);
	}

	/** Adds a block event on the selected object for the block under the mouse. */
	private void blockClick(Picking.Ray ray) {
		SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
		if (o == null) {
			screen.status("Select the object that does it first");
			return;
		}
		var hit = Picking.pickBlock(ray).orElse(null);
		if (hit == null) {
			return;
		}
		int tick = ClientScene.tick();
		switch (EditorState.blockAction) {
			case BREAK -> {
				EventTools.block(o, "break_block", hit.getBlockPos(), null, tick);
				screen.status("Break at tick " + tick);
			}
			case PLACE -> {
				EventTools.block(o, "place_block", hit.getBlockPos().relative(hit.getDirection()), EditorState.blockState, tick);
				screen.status("Place " + EditorState.blockState + " at tick " + tick);
			}
			case USE -> {
				EventTools.block(o, "use_block", hit.getBlockPos(), null, tick);
				screen.status("Use at tick " + tick);
			}
		}
	}

	/** Finds a control point of the selected path close to the mouse ray. */
	private int pickPathPoint(Scene scene, Picking.Ray ray) {
		if (EditorState.selectedPath == null) {
			return -1;
		}
		MotionPath path = scene.path(EditorState.selectedPath).orElse(null);
		if (path == null) {
			return -1;
		}
		net.minecraft.world.phys.Vec3 from = ray.from();
		net.minecraft.world.phys.Vec3 dir = ray.direction();
		int best = -1;
		double bestDist = 0.6;
		for (int i = 0; i < path.points().size(); i++) {
			Vec3 p = path.points().get(i).pos();
			net.minecraft.world.phys.Vec3 v = new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z()).subtract(from);
			double along = v.dot(dir);
			if (along < 0) {
				continue;
			}
			double dist = v.subtract(dir.scale(along)).length();
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	boolean mouseDragged(double x, double y, int button) {
		lastMouseX = x;
		lastMouseY = y;
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (draggingActor || EditorState.dragPoint >= 0 || EditorState.dragHandle != 0) {
			Vec3 at = snapped(screen.groundUnderMouse(x, y));
			if (at != null) {
				EditorState.dragPreview = at;
			}
			return true;
		}
		if (EditorState.tool == EditorState.Tool.PATH && sketchStart >= 0) {
			// Holding the button while drawing sketches freehand; the stroke is thinned when released.
			Vec3 at = screen.groundUnderMouse(x, y);
			if (at != null && at.distanceTo(sketch.getLast()) >= 0.5) {
				sketch.add(at);
				EditorState.pathDraft.add(EditorState.pathDraftAir ? at.add(0, AIR_HEIGHT, 0) : at);
			}
			return true;
		}
		return false;
	}

	/** Thins a freehand stroke down to the few points needed to follow it within a third of a block. */
	private void finishSketch() {
		if (sketchStart < 0) {
			return;
		}
		if (sketch.size() >= 3 && sketchStart + sketch.size() == EditorState.pathDraft.size()) {
			List<Vec3> flat = new ArrayList<>();
			for (Vec3 p : sketch) {
				flat.add(p.withY(0));
			}
			List<Vec3> kept = new ArrayList<>();
			for (int i : io.github.firestormfmd.scenescripter.core.capture.Rdp.simplify(flat, 0.35)) {
				Vec3 p = sketch.get(i);
				kept.add(EditorState.pathDraftAir ? p.add(0, AIR_HEIGHT, 0) : p);
			}
			while (EditorState.pathDraft.size() > sketchStart) {
				EditorState.pathDraft.removeLast();
			}
			EditorState.pathDraft.addAll(kept);
			screen.status("Sketch simplified to " + kept.size() + " points");
		}
		sketch.clear();
		sketchStart = -1;
	}

	/** A handle of the selected path point under the ray: -1 in, 1 out, 0 none. */
	private int pickHandle(Scene scene, Picking.Ray ray) {
		PathPoint p = selectedPathPoint(scene);
		if (p == null || p.handleOut() == null) {
			return 0;
		}
		for (int side : new int[] {-1, 1}) {
			Vec3 h = p.pos().add(side < 0 ? p.handleIn() : p.handleOut());
			net.minecraft.world.phys.Vec3 v = new net.minecraft.world.phys.Vec3(h.x(), h.y(), h.z()).subtract(ray.from());
			double along = v.dot(ray.direction());
			if (along > 0 && v.subtract(ray.direction().scale(along)).length() < 0.5) {
				return side;
			}
		}
		return 0;
	}

	static PathPoint selectedPathPoint(Scene scene) {
		MotionPath path = EditorState.selectedPath == null ? null : scene.path(EditorState.selectedPath).orElse(null);
		if (path == null || EditorState.selectedPoint < 0 || EditorState.selectedPoint >= path.points().size()) {
			return null;
		}
		return path.points().get(EditorState.selectedPoint);
	}

	private static void editPoints(MotionPath path, String label, java.util.function.Consumer<List<PathPoint>> change) {
		List<PathPoint> points = new ArrayList<>(path.points());
		change.accept(points);
		ClientNet.edit(new Edits.SetPathPoints(path.id(), points, label));
	}

	boolean mouseReleased(double x, double y, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		finishSketch();
		Vec3 target = EditorState.dragPreview;
		EditorState.dragPreview = null;
		if (EditorState.dragHandle != 0) {
			int side = EditorState.dragHandle;
			EditorState.dragHandle = 0;
			Scene scene = ClientScene.scene().orElse(null);
			MotionPath path = scene == null || EditorState.selectedPath == null ? null : scene.path(EditorState.selectedPath).orElse(null);
			PathPoint p = scene == null ? null : selectedPathPoint(scene);
			if (path != null && p != null && target != null) {
				Vec3 offset = target.subtract(p.pos());
				Vec3 handle = path.kind() == PathKind.GROUND ? offset.withY(0) : offset.add(0, AIR_HEIGHT, 0);
				int index = EditorState.selectedPoint;
				// Handles stay mirrored so the curve passes smoothly through the point.
				editPoints(path, "Shape path", pts -> pts.set(index, new PathPoint(p.pos(),
						side < 0 ? handle : handle.scale(-1), side < 0 ? handle.scale(-1) : handle)));
			}
			return true;
		}
		if (draggingActor) {
			draggingActor = false;
			SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
			if (o != null && target != null) {
				EditActions.key(o, BuiltInChannels.POSITION, target);
				screen.status("Moved and keyed at tick " + ClientScene.tick());
			}
			return true;
		}
		if (EditorState.dragPoint >= 0) {
			int index = EditorState.dragPoint;
			EditorState.dragPoint = -1;
			Scene scene = ClientScene.scene().orElse(null);
			MotionPath path = scene == null || EditorState.selectedPath == null ? null : scene.path(EditorState.selectedPath).orElse(null);
			if (path != null && target != null && index < path.points().size()) {
				List<PathPoint> points = new ArrayList<>(path.points());
				Vec3 moved = path.kind() == PathKind.AIR ? target.add(0, AIR_HEIGHT, 0) : target;
				points.set(index, points.get(index).withPos(moved));
				ClientNet.edit(new Edits.SetPathPoints(path.id(), points, "Move path point"));
			}
			return true;
		}
		return false;
	}

	boolean keyPressed(int key) {
		if (EditorState.tool == EditorState.Tool.PATH) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				finishPath();
				return true;
			}
			if (key == GLFW.GLFW_KEY_TAB) {
				EditorState.pathDraftAir = !EditorState.pathDraftAir;
				return true;
			}
		}
		if (EditorState.tool == EditorState.Tool.BLOCKS) {
			switch (key) {
				case GLFW.GLFW_KEY_1 -> {
					EditorState.blockAction = EditorState.BlockAction.BREAK;
					return true;
				}
				case GLFW.GLFW_KEY_2 -> {
					EditorState.blockAction = EditorState.BlockAction.PLACE;
					return true;
				}
				case GLFW.GLFW_KEY_3 -> {
					EditorState.blockAction = EditorState.BlockAction.USE;
					return true;
				}
				case GLFW.GLFW_KEY_B -> {
					screen.editText(screen.viewportX() + 6, screen.viewportY() + 6, 200, EditorState.blockState,
							v -> EditorState.blockState = v.trim().isEmpty() ? "minecraft:stone" : v.trim());
					return true;
				}
				default -> {
				}
			}
		}
		Scene scene = ClientScene.scene().orElse(null);
		MotionPath selectedPath = scene == null || EditorState.selectedPath == null ? null
				: scene.path(EditorState.selectedPath).orElse(null);
		if (selectedPath != null && EditorState.selectedPoint >= 0 && EditorState.selectedPoint < selectedPath.points().size()) {
			int i = EditorState.selectedPoint;
			List<PathPoint> pts = selectedPath.points();
			if (key == GLFW.GLFW_KEY_N) {
				// A new point halfway to the next one, or beyond the last.
				Vec3 a = pts.get(i).pos();
				Vec3 b = i + 1 < pts.size() ? pts.get(i + 1).pos() : a.add(a.subtract(i > 0 ? pts.get(i - 1).pos() : a.add(-2, 0, 0)));
				editPoints(selectedPath, "Insert path point", list -> list.add(i + 1, PathPoint.at(Vec3.lerp(a, b, 0.5))));
				EditorState.selectedPoint = i + 1;
				return true;
			}
			if (key == GLFW.GLFW_KEY_H) {
				PathPoint p = pts.get(i);
				if (p.handleOut() != null) {
					editPoints(selectedPath, "Automatic curve", list -> list.set(i, PathPoint.at(p.pos())));
					screen.status("Point back to an automatic curve");
				} else {
					Vec3 prev = i > 0 ? pts.get(i - 1).pos() : p.pos();
					Vec3 next = i + 1 < pts.size() ? pts.get(i + 1).pos() : p.pos();
					Vec3 dir = next.subtract(prev).scale(1 / 6.0);
					if (selectedPath.kind() == PathKind.GROUND) {
						dir = dir.withY(0);
					}
					Vec3 out = dir.length() < 1.0e-6 ? new Vec3(1, 0, 0) : dir;
					editPoints(selectedPath, "Bezier handles", list -> list.set(i, new PathPoint(p.pos(), out.scale(-1), out)));
					screen.status("Drag the white handles to shape the curve; H again for automatic");
				}
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_G) {
			EditorState.snap = EditorState.snap.next();
			screen.status("Snap: " + switch (EditorState.snap) {
				case OFF -> "off";
				case HALF -> "half blocks";
				case BLOCK -> "block centres";
			});
			return true;
		}
		if (key == GLFW.GLFW_KEY_R) {
			SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
			if (o != null) {
				double yaw = EditActions.valueNow(o, BuiltInChannels.BODY_YAW);
				EditActions.key(o, BuiltInChannels.BODY_YAW, yaw + 45);
				return true;
			}
		}
		return false;
	}

	private void finishPath() {
		Scene scene = ClientScene.scene().orElse(null);
		if (scene == null || EditorState.pathDraft.size() < 2) {
			screen.status("A path needs at least two points");
			return;
		}
		String id = EditActions.freshId(scene, "p");
		MotionPath path = new MotionPath(id, "Path " + (scene.paths().size() + 1),
				EditorState.pathDraftAir ? PathKind.AIR : PathKind.GROUND);
		for (Vec3 p : EditorState.pathDraft) {
			path.points().add(PathPoint.at(p));
		}
		ClientNet.edit(new Edits.AddPath(path));
		EditorState.pathDraft.clear();
		EditorState.selectPath(id);
		screen.setTool(EditorState.Tool.SELECT);
		screen.status("Path added. Select an object and use \"Walk\" to make it follow the path");
	}
}
