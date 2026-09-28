package io.github.firestormfmd.scenescripter.core.anim;

/**
 * Bézier handles of a single-component keyframe, as offsets from the keyframe in (ticks, value).
 * The in handle points back in time and the out handle forward; offsets that cross the neighbouring keyframe are
 * clamped when evaluated.
 */
public record Handles(double inDt, double inDv, double outDt, double outDv) {
}
