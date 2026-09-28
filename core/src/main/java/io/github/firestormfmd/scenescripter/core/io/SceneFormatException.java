package io.github.firestormfmd.scenescripter.core.io;

/**
 * A scene file could not be read.
 */
public class SceneFormatException extends Exception {
	public SceneFormatException(String message) {
		super(message);
	}

	public SceneFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
