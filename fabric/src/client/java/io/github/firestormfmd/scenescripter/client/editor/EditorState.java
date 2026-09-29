package io.github.firestormfmd.scenescripter.client.editor;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * What the editor is doing: selection, active tool and timeline view. Lives as long as the client, so reopening
 * the editor keeps its state.
 */
public final class EditorState {
	public enum Tool {
		SELECT, PLACE, PATH, BLOCKS
	}

	/** What a click does with the block tool. */
	public enum BlockAction {
		BREAK, PLACE, USE
	}

	public static @Nullable String selectedObject;
	public static @Nullable String selectedPath;
	public static @Nullable String selectedChannel;
	public static int selectedKeyTick = -1;
	public static @Nullable String hoveredObject;
	public static Tool tool = Tool.SELECT;
	public static BlockAction blockAction = BlockAction.BREAK;
	/** Objects hidden in the editor; their actors disappear only while the editor is open. */
	public static final java.util.Set<String> hidden = new java.util.HashSet<>();
	/** Objects that can't be selected or dragged in the viewport. */
	public static final java.util.Set<String> locked = new java.util.HashSet<>();
	/** The only object shown, or null. */
	public static @Nullable String solo;
	/** Selected keyframes as channel name and tick, for moving, copying and deleting several at once. */
	public static final java.util.Set<KeyRef> selectedKeys = new java.util.HashSet<>();
	/** Copied keyframes, relative to the first one's tick. */
	public static final List<CopiedKey> clipboard = new ArrayList<>();
	public static Snap snap = Snap.OFF;

	public record KeyRef(String channel, int tick) {
	}

	public record CopiedKey(String channel, io.github.firestormfmd.scenescripter.core.anim.ValueType<?> type, Object defaultValue,
			int offset, io.github.firestormfmd.scenescripter.core.anim.Keyframe<?> key) {
	}

	/** Grid that dragged and placed things snap to. */
	public enum Snap {
		OFF, HALF, BLOCK;

		public Snap next() {
			return values()[(ordinal() + 1) % values().length];
		}

		/** Snaps x and z to the grid; y stays on the ground. */
		public Vec3 apply(Vec3 v) {
			return switch (this) {
				case OFF -> v;
				case HALF -> new Vec3(Math.round(v.x() * 2) / 2.0, v.y(), Math.round(v.z() * 2) / 2.0);
				case BLOCK -> new Vec3(Math.floor(v.x()) + 0.5, v.y(), Math.floor(v.z()) + 0.5);
			};
		}
	}

	/** Shows the selected numeric channel as a curve instead of rows of keys. */
	public static boolean curveMode;
	/** Labels the timeline ruler in seconds instead of ticks. */
	public static boolean rulerSeconds;
	/** Shows the panel listing every shortcut (F1). */
	public static boolean helpOpen;
	public static io.github.firestormfmd.scenescripter.core.crowd.Formation crowdFormation =
			io.github.firestormfmd.scenescripter.core.crowd.Formation.GRID;
	public static int crowdCount = 8;
	public static double crowdSpacing = 1.5;
	/** Ticks the scene plays before a capture starts recording. */
	public static int capturePreroll = 60;
	/** Block state the block tool places, in command syntax. */
	public static String blockState = "minecraft:stone";
	/** Entity type placed by the place tool. */
	public static String placeType = "minecraft:zombie";
	/** Control points of a path being drawn. */
	public static final List<Vec3> pathDraft = new ArrayList<>();
	public static boolean pathDraftAir;
	/** Visible tick range of the timeline. */
	public static int viewStart = 0;
	public static int viewEnd = 400;
	public static boolean paletteOpen;
	public static String paletteFilter = "";
	public static boolean sceneBrowserOpen;
	/** Where the selected actor would go while it is being dragged. */
	public static @Nullable Vec3 dragPreview;
	/** Index of the path point being dragged, or -1. */
	public static int dragPoint = -1;
	/** The selected point of the selected path, for inserting, deleting and handles; -1 for none. */
	public static int selectedPoint = -1;
	/** The handle of the selected point being dragged: -1 in, 1 out, 0 none. */
	public static int dragHandle;
	/** The last path selected, offered when giving an object a motion clip. */
	public static @Nullable String lastPath;

	private EditorState() {
	}

	/** Objects whose actors the server should hide while this editor is open: hidden ones, or all but the solo. */
	public static java.util.List<String> effectiveHidden(io.github.firestormfmd.scenescripter.core.scene.Scene scene) {
		java.util.List<String> out = new ArrayList<>();
		for (var o : scene.objects()) {
			if (hidden.contains(o.id()) || (solo != null && !solo.equals(o.id()))) {
				out.add(o.id());
			}
		}
		return out;
	}

	public static void selectObject(@Nullable String id) {
		selectedKeys.clear();
		selectedObject = id;
		selectedPath = null;
		selectedChannel = null;
		selectedKeyTick = -1;
	}

	public static void selectPath(@Nullable String id) {
		selectedPoint = -1;
		selectedPath = id;
		if (id != null) {
			lastPath = id;
		}
		selectedObject = null;
		selectedChannel = null;
		selectedKeyTick = -1;
	}
}
