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
	/** The last path selected, offered when giving an object a motion clip. */
	public static @Nullable String lastPath;

	private EditorState() {
	}

	public static void selectObject(@Nullable String id) {
		selectedObject = id;
		selectedPath = null;
		selectedChannel = null;
		selectedKeyTick = -1;
	}

	public static void selectPath(@Nullable String id) {
		selectedPath = id;
		if (id != null) {
			lastPath = id;
		}
		selectedObject = null;
		selectedChannel = null;
		selectedKeyTick = -1;
	}
}
