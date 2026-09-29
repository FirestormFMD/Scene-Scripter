package io.github.firestormfmd.scenescripter.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.net.Payloads;

/**
 * The scene editor: panels around the live world, like Axiom. The world stays visible and keeps running behind
 * the panels; hold the right mouse button in the viewport to fly the camera.
 */
public final class EditorScreen extends Screen {
	static final int TOP = 20;
	static final int LEFT = 150;
	static final int RIGHT = 190;
	static final int BOTTOM = 110;

	private final Ui ui = new Ui();
	private final EditorCamera camera = new EditorCamera();
	private final Outliner outliner = new Outliner(this);
	private final Inspector inspector = new Inspector(this);
	private final Timeline timeline = new Timeline(this);
	private final Viewport viewport = new Viewport(this);

	private @Nullable EditBox field;
	private @Nullable Consumer<String> fieldCommit;
	private String status = "";
	/** The Apply button was clicked once and waits for a second click. */
	private boolean confirmApply;
	private int statusTicks;

	public EditorScreen() {
		super(Component.translatable("scenescripter.editor.title"));
	}

	Ui ui() {
		return ui;
	}

	EditorCamera camera() {
		return camera;
	}

	Timeline timeline() {
		return timeline;
	}

	void status(String message) {
		status = message;
		statusTicks = 100;
	}

	int viewportX() {
		return LEFT;
	}

	int viewportY() {
		return TOP;
	}

	int viewportWidth() {
		return width - LEFT - RIGHT;
	}

	int viewportHeight() {
		return height - TOP - BOTTOM;
	}

	boolean inViewport(double x, double y) {
		return x >= LEFT && x < width - RIGHT && y >= TOP && y < height - BOTTOM;
	}

	@Override
	protected void init() {
		field = new EditBox(font, 0, 0, 100, 12, Component.empty());
		field.visible = false;
		field.setMaxLength(256);
		addRenderableWidget(field);
	}

	/** Opens a one-line text field over a UI element; Enter commits, Escape cancels. */
	void editText(int x, int y, int w, String value, Consumer<String> commit) {
		if (field == null) {
			return;
		}
		field.setX(x);
		field.setY(y);
		field.setWidth(w);
		field.setValue(value);
		field.visible = true;
		setFocused(field);
		field.setFocused(true);
		fieldCommit = commit;
	}

	boolean editing() {
		return field != null && field.visible;
	}

	private void closeField(boolean commit) {
		if (field == null || !field.visible) {
			return;
		}
		String value = field.getValue();
		field.visible = false;
		field.setFocused(false);
		setFocused(null);
		Consumer<String> c = fieldCommit;
		fieldCommit = null;
		if (commit && c != null) {
			c.accept(value);
		}
	}

	@Override
	public boolean isPauseScreen() {
		// The integrated server must keep ticking so the scene can play.
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// No dimming or blur: the world is the viewport.
	}

	@Override
	public void tick() {
		camera.tick();
		if (statusTicks > 0) {
			statusTicks--;
		}
		viewport.tick();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		ui.begin(graphics, font, mouseX, mouseY);
		Scene scene = ClientScene.scene().orElse(null);
		topBar(scene);
		if (scene == null || EditorState.sceneBrowserOpen) {
			sceneBrowser(scene);
		} else {
			outliner.draw(scene, 0, TOP, LEFT, height - TOP - BOTTOM);
			inspector.draw(scene, width - RIGHT, TOP, RIGHT, height - TOP - BOTTOM);
			timeline.draw(scene, 0, height - BOTTOM, width, BOTTOM);
			viewport.drawOverlay(scene);
			if (EditorState.paletteOpen) {
				palette();
			}
		}
		if (statusTicks > 0 && !status.isEmpty()) {
			int w = ui.width(status) + 10;
			ui.fill((width - w) / 2, TOP + 6, w, 14, Ui.PANEL_DARK);
			ui.text(status, (width - w) / 2 + 5, TOP + 9, Ui.TEXT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		ui.end();
	}

	private void topBar(@Nullable Scene scene) {
		ui.panel(0, 0, width, TOP);
		int x = 4;
		String title = scene == null ? "No scene open" : ClientScene.name() + (ClientScene.state().dirty() ? " *" : "");
		ui.text(ui.fit(title, 120), x, 6, Ui.TEXT);
		x += 124;
		ui.button(x, 3, 50, 14, "Scenes", EditorState.sceneBrowserOpen, () -> {
			EditorState.sceneBrowserOpen = !EditorState.sceneBrowserOpen;
			ClientNet.sceneCommand(Payloads.SceneCommand.LIST, "", 0);
		});
		x += 52;
		if (scene != null) {
			ui.button(x, 3, 36, 14, "Save", false, () -> ClientNet.sceneCommand(Payloads.SceneCommand.SAVE, "", 0));
			x += 38;
			ui.button(x, 3, 40, 14, "Close", false, () -> ClientNet.sceneCommand(Payloads.SceneCommand.CLOSE, "", 0));
			x += 46;
			String undo = ClientScene.state().undoLabel();
			String redo = ClientScene.state().redoLabel();
			ui.button(x, 3, 70, 14, undo.isEmpty() ? "Undo" : "Undo " + undo, false, ClientNet::undo);
			x += 72;
			ui.button(x, 3, 70, 14, redo.isEmpty() ? "Redo" : "Redo " + redo, false, ClientNet::redo);
			x += 78;
			ui.button(x, 3, 42, 14, "Select", EditorState.tool == EditorState.Tool.SELECT, () -> setTool(EditorState.Tool.SELECT));
			x += 44;
			ui.button(x, 3, 42, 14, "Place", EditorState.tool == EditorState.Tool.PLACE, () -> {
				setTool(EditorState.Tool.PLACE);
				EditorState.paletteOpen = true;
			});
			x += 44;
			ui.button(x, 3, 42, 14, "Path", EditorState.tool == EditorState.Tool.PATH, () -> setTool(EditorState.Tool.PATH));
			x += 44;
			ui.button(x, 3, 44, 14, "Blocks", EditorState.tool == EditorState.Tool.BLOCKS, () -> setTool(EditorState.Tool.BLOCKS));
			x += 50;
			ui.button(x, 3, 44, 14, "Bounds", false, () -> {
				ClientNet.sceneCommand(Payloads.SceneCommand.FIT_BOUNDS, "", 0);
				status("Scene bounds fitted around everything; their chunks stay loaded");
			});
			x += 46;
			ui.button(x, 3, 46, 14, "Record", false, () -> {
				// Closing the editor hides every overlay, so the recorder sees only the scene.
				ClientNet.sceneCommand(Payloads.SceneCommand.RECORD, "", 40);
				EditorMode.close();
			});
			x += 48;
			ui.button(x, 3, 40, 14, confirmApply ? "Sure?" : "Apply", confirmApply, () -> {
				if (confirmApply) {
					ClientNet.sceneCommand(Payloads.SceneCommand.APPLY, "", 0);
					confirmApply = false;
				} else {
					confirmApply = true;
					status("Apply keeps the scene's block changes up to the playhead for good. Click again to confirm");
				}
			});
		}
		String help = "RMB: fly  Space: play  I: key  Del: delete  Ctrl+Z/Y  Right Ctrl: close";
		ui.text(ui.fit(help, Math.max(0, width - x - 60)), Math.max(x + 50, width - ui.width(help) - 6), 6, Ui.TEXT_DIM);
	}

	void setTool(EditorState.Tool tool) {
		EditorState.tool = tool;
		confirmApply = false;
		if (tool != EditorState.Tool.PATH) {
			EditorState.pathDraft.clear();
		}
		if (tool != EditorState.Tool.PLACE) {
			EditorState.paletteOpen = false;
		}
		status(switch (tool) {
			case SELECT -> "Select: click an actor; drag to move it and key its position";
			case PLACE -> "Place: pick a type, then click the ground";
			case PATH -> "Path: click the ground to add points, Enter to finish (Tab for an air path)";
			case BLOCKS -> "Blocks: the selected object breaks, places or uses the clicked block at the playhead";
		});
	}

	private void sceneBrowser(@Nullable Scene scene) {
		int w = 260;
		int h = 220;
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		ui.panel(x, y, w, h);
		ui.text("Scenes in this world", x + 8, y + 8, Ui.TEXT);
		int rowY = y + 24;
		List<String> names = ClientScene.sceneList();
		if (names.isEmpty()) {
			ui.text("None yet. Create one below.", x + 8, rowY, Ui.TEXT_DIM);
		}
		for (String name : names) {
			if (rowY > y + h - 60) {
				break;
			}
			boolean current = name.equals(ClientScene.name());
			ui.text(ui.fit(name, w - 110), x + 8, rowY + 2, current ? Ui.KEY : Ui.TEXT);
			ui.button(x + w - 96, rowY, 44, 12, current ? "Open" : "Open", current, () -> {
				ClientNet.sceneCommand(Payloads.SceneCommand.OPEN, name, 0);
				EditorState.sceneBrowserOpen = false;
			});
			ui.button(x + w - 50, rowY, 42, 12, "Delete", false, () -> ClientNet.sceneCommand(Payloads.SceneCommand.DELETE, name, 0));
			rowY += 14;
		}
		int by = y + h - 30;
		ui.button(x + 8, by, 120, 16, "New scene...", false, () -> editText(x + 8, by - 16, 160, "", name -> {
			ClientNet.sceneCommand(Payloads.SceneCommand.NEW, name, 20 * 60);
			EditorState.sceneBrowserOpen = false;
		}));
		if (scene != null) {
			ui.button(x + w - 70, by, 62, 16, "Back", false, () -> EditorState.sceneBrowserOpen = false);
		}
	}

	private List<String> paletteTypes() {
		List<String> ids = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
			ids.add(id.toString());
		}
		ids.sort(String::compareTo);
		String filter = EditorState.paletteFilter.toLowerCase(Locale.ROOT);
		ids.removeIf(id -> !filter.isEmpty() && !id.contains(filter));
		return ids;
	}

	private void palette() {
		int w = 200;
		int h = Math.min(260, height - TOP - BOTTOM - 20);
		int x = LEFT + 10;
		int y = TOP + 10;
		ui.panel(x, y, w, h);
		ui.text("Place what?", x + 6, y + 6, Ui.TEXT);
		ui.button(x + w - 16, y + 3, 13, 12, "x", false, () -> EditorState.paletteOpen = false);
		ui.button(x + 6, y + 18, w - 12, 12, EditorState.paletteFilter.isEmpty() ? "Search..." : "Search: " + EditorState.paletteFilter,
				false, () -> editText(x + 6, y + 18, w - 12, EditorState.paletteFilter, v -> EditorState.paletteFilter = v));
		ui.button(x + 6, y + 32, w - 12, 12, "Player (Mannequin)", EditorState.placeType.equals("minecraft:mannequin"),
				() -> EditorState.placeType = "minecraft:mannequin");
		int rowY = y + 48;
		for (String id : paletteTypes()) {
			if (rowY > y + h - 14) {
				break;
			}
			boolean chosen = id.equals(EditorState.placeType);
			ui.button(x + 6, rowY, w - 12, 11, EditActions.prettyName(id), chosen, () -> EditorState.placeType = id);
			rowY += 12;
		}
	}

	// ---- Input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();
		if (editing() && field != null && field.isMouseOver(x, y)) {
			return super.mouseClicked(event, doubleClick);
		}
		closeField(true);
		if (ui.click(x, y, event.button())) {
			return true;
		}
		if (inViewport(x, y)) {
			return viewport.mouseClicked(x, y, event.button());
		}
		return timeline.mouseClicked(x, y, event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (camera.isLooking()) {
			camera.look(dx, dy);
			return true;
		}
		if (timeline.mouseDragged(event.x(), event.y(), event.button())) {
			return true;
		}
		return viewport.mouseDragged(event.x(), event.y(), event.button()) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && camera.isLooking()) {
			camera.setLooking(false);
			return true;
		}
		if (timeline.mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return viewport.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (camera.isLooking()) {
			camera.scroll(scrollY);
			return true;
		}
		if (timeline.mouseScrolled(x, y, scrollY) || inspector.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (editing()) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				closeField(true);
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				closeField(false);
				return true;
			}
			return super.keyPressed(event);
		}
		if (camera.keyPressed(key)) {
			return true;
		}
		if (EditorMode.isToggleKey(key)) {
			onClose();
			return true;
		}
		boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
		Scene scene = ClientScene.scene().orElse(null);
		if (ctrl && key == GLFW.GLFW_KEY_Z) {
			ClientNet.undo();
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_Y) {
			ClientNet.redo();
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_S) {
			ClientNet.sceneCommand(Payloads.SceneCommand.SAVE, "", 0);
			return true;
		}
		if (scene != null) {
			if (key == GLFW.GLFW_KEY_SPACE) {
				if (ClientScene.state().playing()) {
					ClientNet.pause();
				} else {
					ClientNet.play();
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
				int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 20 : 1;
				ClientNet.seek(Math.max(0, ClientScene.tick() + (key == GLFW.GLFW_KEY_LEFT ? -step : step)));
				return true;
			}
			if (key == GLFW.GLFW_KEY_HOME) {
				ClientNet.seek(0);
				return true;
			}
			if (key == GLFW.GLFW_KEY_END) {
				ClientNet.seek(scene.length());
				return true;
			}
			if (key == GLFW.GLFW_KEY_I) {
				inspector.keyPosition(scene);
				return true;
			}
			if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
				deleteSelection(scene);
				return true;
			}
			if (viewport.keyPressed(key)) {
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (EditorState.paletteOpen || EditorState.sceneBrowserOpen || !EditorState.pathDraft.isEmpty()) {
				EditorState.paletteOpen = false;
				EditorState.sceneBrowserOpen = false;
				EditorState.pathDraft.clear();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		camera.keyReleased(event.key());
		return super.keyReleased(event);
	}

	private void deleteSelection(Scene scene) {
		SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
		if (o != null && EditorState.selectedChannel != null && EditorState.selectedKeyTick >= 0) {
			EditActions.deleteKey(o, EditorState.selectedChannel, EditorState.selectedKeyTick);
			EditorState.selectedKeyTick = -1;
		} else if (o != null) {
			EditActions.delete(o);
		} else if (EditorState.selectedPath != null) {
			ClientNet.edit(new io.github.firestormfmd.scenescripter.core.edit.Edits.RemovePath(EditorState.selectedPath));
			EditorState.selectPath(null);
		}
	}

	/** Starts flying the camera while the right mouse button is held in the viewport. */
	void startLooking() {
		camera.setLooking(true);
	}

	@Override
	public void onClose() {
		closeField(false);
		EditorMode.close();
	}

	/** Ground point under the mouse, for placing things. */
	@Nullable Vec3 groundUnderMouse(double x, double y) {
		Picking.Ray ray = Picking.ray(x, y, width, height);
		BlockHitResult hit = Picking.pickBlock(ray).orElse(null);
		if (hit == null) {
			return null;
		}
		net.minecraft.world.phys.Vec3 at = hit.getLocation();
		return new Vec3(at.x(), at.y(), at.z());
	}
}
