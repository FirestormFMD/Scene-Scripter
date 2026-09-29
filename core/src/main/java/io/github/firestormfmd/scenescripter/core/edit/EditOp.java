package io.github.firestormfmd.scenescripter.core.edit;

import io.github.firestormfmd.scenescripter.core.scene.Scene;

/**
 * One edit to a scene. Every change the editor makes goes through an edit op, so it can be undone and, later, sent
 * between client and server.
 */
public interface EditOp {
	/**
	 * Applies this edit. Either the whole edit is applied or, if it throws, the scene is left unchanged.
	 *
	 * @return the edit that undoes this one
	 */
	EditOp apply(Scene scene);

	/** Short description for the undo history, such as "Add keyframe". */
	String label();
}
