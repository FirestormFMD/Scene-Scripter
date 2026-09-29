package io.github.firestormfmd.scenescripter.core.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.anim.Channel;

/**
 * A scene-controlled entity. In the world it is a real vanilla entity with its autonomy switched off; this class
 * is its timeline.
 */
public final class SceneObject {
	private final String id;
	private String name;
	private String entityType;
	private final Map<String, String> appearance = new LinkedHashMap<>();
	private int spawnTick;
	/** Tick the object leaves the scene, or -1 to stay until the end. */
	private int despawnTick = -1;
	private String group;
	/** ID of the event whose solved result created this object, such as a chain-reaction TNT, or null. */
	private String generatedBy;
	private final List<MotionClip> motion = new ArrayList<>();
	private final Map<String, Channel<?>> channels = new LinkedHashMap<>();
	private final List<SceneEvent> events = new ArrayList<>();
	private InteractionRules rules = InteractionRules.INHERIT;

	/**
	 * @param entityType vanilla entity type ID, such as {@code minecraft:zombie}; player objects use
	 *                   {@code minecraft:mannequin}
	 */
	public SceneObject(String id, String name, String entityType) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.entityType = Objects.requireNonNull(entityType, "entityType");
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

	public String entityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = Objects.requireNonNull(entityType, "entityType");
	}

	/** Spawn-time look, such as {@code skin}, {@code model}, {@code variant} or {@code baby}. Mutable. */
	public Map<String, String> appearance() {
		return appearance;
	}

	public int spawnTick() {
		return spawnTick;
	}

	public int despawnTick() {
		return despawnTick;
	}

	public void setLifetime(int spawnTick, int despawnTick) {
		if (despawnTick != -1 && despawnTick <= spawnTick) {
			throw new IllegalArgumentException("An object must despawn after it spawns");
		}
		this.spawnTick = spawnTick;
		this.despawnTick = despawnTick;
	}

	/** Whether the object exists at the given tick. */
	public boolean existsAt(int tick) {
		return tick >= spawnTick && (despawnTick == -1 || tick < despawnTick);
	}

	public String group() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String generatedBy() {
		return generatedBy;
	}

	public void setGeneratedBy(String generatedBy) {
		this.generatedBy = generatedBy;
	}

	public boolean isGenerated() {
		return generatedBy != null;
	}

	/** Motion clips sorted by start tick. */
	public List<MotionClip> motion() {
		return Collections.unmodifiableList(motion);
	}

	public void addMotion(MotionClip clip) {
		motion.add(clip);
		motion.sort(Comparator.comparingInt(MotionClip::startTick));
	}

	public boolean removeMotion(MotionClip clip) {
		return motion.remove(clip);
	}

	public Map<String, Channel<?>> channels() {
		return Collections.unmodifiableMap(channels);
	}

	public Optional<Channel<?>> channel(String name) {
		return Optional.ofNullable(channels.get(name));
	}

	/**
	 * Returns the channel, creating it from its spec if it does not exist yet.
	 */
	@SuppressWarnings("unchecked")
	public <T> Channel<T> channel(ChannelSpec<T> spec) {
		Channel<?> existing = channels.get(spec.name());
		if (existing == null) {
			Channel<T> created = spec.create();
			channels.put(spec.name(), created);
			return created;
		}
		if (existing.type() != spec.type()) {
			throw new IllegalStateException("Channel " + spec.name() + " holds " + existing.type() + ", not " + spec.type());
		}
		return (Channel<T>) existing;
	}

	/** Adds or replaces a channel, such as a custom variable or one read from a file. */
	public void putChannel(String name, Channel<?> channel) {
		channels.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(channel, "channel"));
	}

	public Optional<Channel<?>> removeChannel(String name) {
		return Optional.ofNullable(channels.remove(name));
	}

	/** Events sorted by tick. */
	public List<SceneEvent> events() {
		return Collections.unmodifiableList(events);
	}

	public void addEvent(SceneEvent event) {
		if (findEvent(event.id()).isPresent()) {
			throw new IllegalArgumentException("Duplicate event ID: " + event.id());
		}
		events.add(event);
		events.sort(Comparator.comparingInt(SceneEvent::tick));
	}

	public Optional<SceneEvent> findEvent(String eventId) {
		return events.stream().filter(e -> e.id().equals(eventId)).findFirst();
	}

	public Optional<SceneEvent> removeEvent(String eventId) {
		Optional<SceneEvent> found = findEvent(eventId);
		found.ifPresent(events::remove);
		return found;
	}

	/** Per-object rule overrides; unset fields inherit from the scene. */
	public InteractionRules rules() {
		return rules;
	}

	public void setRules(InteractionRules rules) {
		this.rules = Objects.requireNonNull(rules, "rules");
	}

	/** Deep copy, so snapshots held by undo history are not changed by later edits. */
	public SceneObject copy() {
		SceneObject o = new SceneObject(id, name, entityType);
		o.appearance.putAll(appearance);
		o.spawnTick = spawnTick;
		o.despawnTick = despawnTick;
		o.group = group;
		o.generatedBy = generatedBy;
		o.motion.addAll(motion);
		channels.forEach((k, v) -> o.channels.put(k, v.copy()));
		o.events.addAll(events);
		o.rules = rules;
		return o;
	}
}
