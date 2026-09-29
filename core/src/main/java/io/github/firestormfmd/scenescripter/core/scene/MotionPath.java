package io.github.firestormfmd.scenescripter.core.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A spline drawn in the world that objects travel along. Several objects can share one path through their own
 * {@link MotionClip}s.
 */
public final class MotionPath {
	private final String id;
	private String name;
	private PathKind kind;
	private final List<PathPoint> points = new ArrayList<>();
	private Gait gait = Gait.WALK;
	private double baseSpeed = Gait.WALK.playerSpeed();
	private final List<SpeedKey> speedKeys = new ArrayList<>();
	private final List<PathMarker> markers = new ArrayList<>();
	/** Jump height in blocks, or null to use the object's vanilla jump. */
	private Double jumpHeight;
	/** Falls longer than this many blocks are flagged; null for no warning. */
	private Double maxDrop;

	public MotionPath(String id, String name, PathKind kind) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.kind = Objects.requireNonNull(kind, "kind");
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = Objects.requireNonNull(name, "name");
	}

	public PathKind kind() {
		return kind;
	}

	public void setKind(PathKind kind) {
		this.kind = Objects.requireNonNull(kind, "kind");
	}

	/** Control points in order. Mutable. */
	public List<PathPoint> points() {
		return points;
	}

	public Gait gait() {
		return gait;
	}

	public void setGait(Gait gait) {
		this.gait = Objects.requireNonNull(gait, "gait");
	}

	/** Speed in blocks per second where no speed key applies. */
	public double baseSpeed() {
		return baseSpeed;
	}

	public void setBaseSpeed(double speed) {
		if (!(speed > 0)) {
			throw new IllegalArgumentException("Speed must be positive: " + speed);
		}
		this.baseSpeed = speed;
	}

	/** Speed keys sorted by position. */
	public List<SpeedKey> speedKeys() {
		return List.copyOf(speedKeys);
	}

	public void setSpeedKeys(List<SpeedKey> keys) {
		speedKeys.clear();
		speedKeys.addAll(keys);
		speedKeys.sort(Comparator.comparingDouble(SpeedKey::at));
	}

	/** Markers sorted by position. */
	public List<PathMarker> markers() {
		return List.copyOf(markers);
	}

	public void setMarkers(List<PathMarker> newMarkers) {
		markers.clear();
		markers.addAll(newMarkers);
		markers.sort(Comparator.comparingDouble(PathMarker::at));
	}

	public Double jumpHeight() {
		return jumpHeight;
	}

	public void setJumpHeight(Double jumpHeight) {
		if (jumpHeight != null && !(jumpHeight > 0)) {
			throw new IllegalArgumentException("Jump height must be positive: " + jumpHeight);
		}
		this.jumpHeight = jumpHeight;
	}

	public Double maxDrop() {
		return maxDrop;
	}

	public void setMaxDrop(Double maxDrop) {
		if (maxDrop != null && !(maxDrop > 0)) {
			throw new IllegalArgumentException("Drop warning height must be positive: " + maxDrop);
		}
		this.maxDrop = maxDrop;
	}

	public MotionPath copy() {
		MotionPath p = new MotionPath(id, name, kind);
		p.points.addAll(points);
		p.gait = gait;
		p.baseSpeed = baseSpeed;
		p.speedKeys.addAll(speedKeys);
		p.markers.addAll(markers);
		p.jumpHeight = jumpHeight;
		p.maxDrop = maxDrop;
		return p;
	}
}
