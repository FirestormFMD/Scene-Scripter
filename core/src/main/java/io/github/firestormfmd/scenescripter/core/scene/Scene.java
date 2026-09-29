package io.github.firestormfmd.scenescripter.core.scene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.math.BlockBox;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;

/**
 * A saved scene: objects, motion paths and settings. Times are in ticks (20 per second).
 */
public final class Scene {
	public static final int FORMAT = 1;

	private String name;
	private int length;
	private BlockPos origin = new BlockPos(0, 0, 0);
	private BlockBox bounds;
	private SceneSettings settings = SceneSettings.DEFAULTS;
	private final Map<String, MotionPath> paths = new LinkedHashMap<>();
	private final Map<String, SceneObject> objects = new LinkedHashMap<>();
	/** Next number to use for each ID prefix, so IDs are never reused within a scene. */
	private final Map<String, Integer> idCounters = new HashMap<>();

	public Scene(String name, int length) {
		this.name = Objects.requireNonNull(name, "name");
		setLength(length);
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = Objects.requireNonNull(name, "name");
	}

	public int length() {
		return length;
	}

	public void setLength(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("Scene length must be positive");
		}
		this.length = length;
	}

	/** Reference point that exported scenes are placed relative to. */
	public BlockPos origin() {
		return origin;
	}

	public void setOrigin(BlockPos origin) {
		this.origin = Objects.requireNonNull(origin, "origin");
	}

	/** The stage; chunks inside stay loaded during playback. Null until set. */
	public BlockBox bounds() {
		return bounds;
	}

	public void setBounds(BlockBox bounds) {
		this.bounds = bounds;
	}

	public SceneSettings settings() {
		return settings;
	}

	public void setSettings(SceneSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	/** Returns a fresh ID with the given prefix, such as {@code o7} for objects. */
	public String newId(String prefix) {
		while (true) {
			int n = idCounters.merge(prefix, 1, Integer::sum);
			String id = prefix + n;
			if (!objects.containsKey(id) && !paths.containsKey(id) && findEventOwner(id).isEmpty()) {
				return id;
			}
		}
	}

	/** Current ID counters, for saving. */
	public Map<String, Integer> idCounters() {
		return Collections.unmodifiableMap(idCounters);
	}

	public void setIdCounter(String prefix, int value) {
		idCounters.put(prefix, value);
	}

	/** ID of the scene's own track, which holds time of day, weather, sounds, commands and markers. */
	public static final String TRACKS_ID = "scene";
	public static final String TRACKS_TYPE = "scenescripter:scene";
	private SceneObject tracks = new SceneObject(TRACKS_ID, "Scene", TRACKS_TYPE);

	/**
	 * The scene's own track. It is edited like an object (keyframes on {@code time_of_day} and {@code weather},
	 * events such as {@code sound}, {@code command} and {@code marker}) but has no actor and is not in
	 * {@link #objects()}.
	 */
	public SceneObject tracks() {
		return tracks;
	}

	public static boolean isTracks(SceneObject o) {
		return TRACKS_ID.equals(o.id());
	}

	public Collection<SceneObject> objects() {
		return Collections.unmodifiableCollection(objects.values());
	}

	public Optional<SceneObject> object(String id) {
		if (TRACKS_ID.equals(id)) {
			return Optional.of(tracks);
		}
		return Optional.ofNullable(objects.get(id));
	}

	public List<String> objectIds() {
		return List.copyOf(objects.keySet());
	}

	public void addObject(SceneObject object) {
		addObject(object, objects.size());
	}

	/** Adds an object at a position in the outliner order. */
	public void addObject(SceneObject object, int index) {
		if (TRACKS_ID.equals(object.id())) {
			tracks = object;
			return;
		}
		if (objects.containsKey(object.id())) {
			throw new IllegalArgumentException("Duplicate object ID: " + object.id());
		}
		insertAt(objects, object.id(), object, index);
	}

	/** Removes an object. The scene track cannot be removed, only replaced: it is returned but stays. */
	public Optional<SceneObject> removeObject(String id) {
		if (TRACKS_ID.equals(id)) {
			return Optional.of(tracks);
		}
		return Optional.ofNullable(objects.remove(id));
	}

	public int indexOfObject(String id) {
		return new ArrayList<>(objects.keySet()).indexOf(id);
	}

	public Collection<MotionPath> paths() {
		return Collections.unmodifiableCollection(paths.values());
	}

	public Optional<MotionPath> path(String id) {
		return Optional.ofNullable(paths.get(id));
	}

	public void addPath(MotionPath path) {
		addPath(path, paths.size());
	}

	public void addPath(MotionPath path, int index) {
		if (paths.containsKey(path.id())) {
			throw new IllegalArgumentException("Duplicate path ID: " + path.id());
		}
		insertAt(paths, path.id(), path, index);
	}

	public Optional<MotionPath> removePath(String id) {
		return Optional.ofNullable(paths.remove(id));
	}

	public int indexOfPath(String id) {
		return new ArrayList<>(paths.keySet()).indexOf(id);
	}

	/** The object that owns the event with this ID, if any. */
	public Optional<SceneObject> findEventOwner(String eventId) {
		if (tracks.findEvent(eventId).isPresent()) {
			return Optional.of(tracks);
		}
		for (SceneObject o : objects.values()) {
			if (o.findEvent(eventId).isPresent()) {
				return Optional.of(o);
			}
		}
		return Optional.empty();
	}

	private static <V> void insertAt(Map<String, V> map, String key, V value, int index) {
		if (index < 0 || index >= map.size()) {
			map.put(key, value);
			return;
		}
		List<Map.Entry<String, V>> entries = new ArrayList<>(map.entrySet());
		entries.add(index, Map.entry(key, value));
		map.clear();
		for (Map.Entry<String, V> e : entries) {
			map.put(e.getKey(), e.getValue());
		}
	}
}
