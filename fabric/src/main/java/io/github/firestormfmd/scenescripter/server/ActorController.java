package io.github.firestormfmd.scenescripter.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.actor.Actors;
import io.github.firestormfmd.scenescripter.core.runtime.ObjectState;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.mixin.MannequinAccessor;

/**
 * Keeps one actor entity per scene object in step with the timeline: spawning, driving, killing, reviving and
 * removing them as the playhead moves.
 */
public final class ActorController {
	/** Ticks of vanilla death animation before the body disappears in a puff of smoke. */
	private static final int DEATH_ANIMATION_TICKS = 20;
	/** Entity event that makes clients spawn the death poof particles. */
	private static final byte POOF_EVENT = 60;

	private final ServerLevel level;
	private final Map<String, Entity> actors = new LinkedHashMap<>();
	private final Map<String, Boolean> wasDead = new HashMap<>();
	private final Map<String, Double> stepDistance = new HashMap<>();
	private final Set<String> reportedMissingTypes = new HashSet<>();

	public ActorController(ServerLevel level) {
		this.level = level;
	}

	public ServerLevel level() {
		return level;
	}

	/** Entity IDs of the actors, by object ID, for the editor to pick them. */
	public Map<String, Integer> entityIds() {
		Map<String, Integer> ids = new LinkedHashMap<>();
		actors.forEach((id, e) -> ids.put(id, e.getId()));
		return ids;
	}

	public Optional<Entity> actor(String objectId) {
		return Optional.ofNullable(actors.get(objectId));
	}

	/**
	 * Brings every actor to its state at {@code tick}.
	 *
	 * @param jump true when the playhead jumped rather than moved forward by one step
	 * @return true if actors were added or removed, so the editor's actor list needs refreshing
	 */
	public boolean update(SceneEvaluator evaluator, int tick, boolean jump) {
		boolean changed = false;
		Set<String> seen = new HashSet<>();
		for (SceneObject o : evaluator.scene().objects()) {
			seen.add(o.id());
			ObjectState s = evaluator.evaluate(o, tick);
			boolean gone = s.dead() && s.ticksDead() >= DEATH_ANIMATION_TICKS;
			boolean visible = s.exists() && !gone;
			Entity e = actors.get(o.id());

			if (e != null && (e.isRemoved() || !typeMatches(e, o))) {
				discard(o.id());
				e = null;
				changed = true;
			}
			if (!visible) {
				if (e != null) {
					if (gone && !jump) {
						level.broadcastEntityEvent(e, POOF_EVENT);
					}
					discard(o.id());
					changed = true;
				}
				continue;
			}
			// Coming back from the dead gets a fresh entity so clients forget the death animation.
			if (e != null && wasDead.getOrDefault(o.id(), false) && !s.dead()) {
				discard(o.id());
				e = null;
				changed = true;
			}
			if (e == null) {
				e = spawn(o, s);
				if (e == null) {
					continue;
				}
				changed = true;
				jump = true;
			}
			ActorApplier.apply(level, e, s, jump);
			wasDead.put(o.id(), s.dead());
			if (!jump) {
				ambientEffects(o, e, s, tick);
			}
		}
		for (Iterator<String> it = actors.keySet().iterator(); it.hasNext(); ) {
			String id = it.next();
			if (!seen.contains(id)) {
				actors.get(id).discard();
				it.remove();
				wasDead.remove(id);
				changed = true;
			}
		}
		return changed;
	}

	/** Removes every actor, for closing a scene or stopping the server. */
	public void removeAll() {
		actors.values().forEach(Entity::discard);
		actors.clear();
		wasDead.clear();
		stepDistance.clear();
	}

	private void discard(String objectId) {
		Entity e = actors.remove(objectId);
		if (e != null) {
			e.discard();
		}
		wasDead.remove(objectId);
	}

	private static boolean typeMatches(Entity e, SceneObject o) {
		return EntityBodies.type(o.entityType()).map(t -> t == e.getType()).orElse(false);
	}

	private Entity spawn(SceneObject o, ObjectState s) {
		Optional<EntityType<?>> type = EntityBodies.type(o.entityType());
		if (type.isEmpty()) {
			if (reportedMissingTypes.add(o.entityType())) {
				SceneScripter.LOGGER.warn("Scene object {} has unknown entity type {}", o.id(), o.entityType());
			}
			return null;
		}
		Entity e = type.get().create(level, EntitySpawnReason.COMMAND);
		if (e == null) {
			return null;
		}
		Actors.mark(e, o.id());
		e.setInvulnerable(true);
		e.setNoGravity(true);
		if (e instanceof Mob mob) {
			mob.setNoAi(true);
		}
		if (e instanceof AgeableMob ageable && "true".equals(o.appearance().get("baby"))) {
			ageable.setAge(-24000);
		}
		if (e instanceof Mannequin mannequin) {
			MannequinAccessor access = (MannequinAccessor) mannequin;
			String skin = o.appearance().getOrDefault("skin", "");
			if (!skin.isBlank()) {
				access.scenescripter$setProfile(ResolvableProfile.createUnresolved(skin));
			}
			access.scenescripter$setHideDescription(true);
			access.scenescripter$setImmovable(true);
		}
		e.snapTo(s.position().x(), s.position().y(), s.position().z(), s.bodyYaw(), s.headPitch());
		if (!level.addFreshEntity(e)) {
			return null;
		}
		actors.put(o.id(), e);
		return e;
	}

	/** Step sounds and idle sounds, played the way a live entity would so they end up in recordings. */
	private void ambientEffects(SceneObject o, Entity e, ObjectState s, int tick) {
		if (s.silent()) {
			return;
		}
		if (s.moving() && s.onGround() && !s.swimming()) {
			double moved = e.position().subtract(e.oldPosition()).horizontalDistance();
			double total = stepDistance.getOrDefault(o.id(), 0.0) + moved;
			if (total >= 1.0) {
				total -= 1.0;
				BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
				BlockState ground = level.getBlockState(below);
				if (!ground.isAir()) {
					SoundType sound = ground.getSoundType();
					e.playSound(sound.getStepSound(), sound.getVolume() * 0.15f, sound.getPitch());
				}
			}
			stepDistance.put(o.id(), total);
		}
		if (s.ambientSounds() && e instanceof Mob mob && Math.floorMod(tick + o.id().hashCode(), 120) == 0) {
			mob.playAmbientSound();
		}
	}
}
