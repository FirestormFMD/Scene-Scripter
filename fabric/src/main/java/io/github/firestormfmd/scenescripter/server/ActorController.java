package io.github.firestormfmd.scenescripter.server;

import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.mixin.AbstractArrowAccessor;
import io.github.firestormfmd.scenescripter.mixin.FallingBlockAccessor;
import io.github.firestormfmd.scenescripter.mixin.LivingEntityInvoker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
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
	/** Thrown items that burst into particles where they land. */
	private static final Set<String> BREAKS_ON_IMPACT = Set.of("minecraft:snowball", "minecraft:egg",
			"minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:experience_bottle", "minecraft:ender_pearl");

	private final ServerLevel level;
	private final Map<String, Entity> actors = new LinkedHashMap<>();
	private final Map<String, Boolean> wasDead = new HashMap<>();
	/** Objects that existed at the last update, so spawn and despawn effects play only when the lifetime turns. */
	private final Set<String> existed = new HashSet<>();
	private final Map<String, Double> stepDistance = new HashMap<>();
	private final Set<String> reportedMissingTypes = new HashSet<>();
	/** The spawn-time look each actor was made with; a change means the actor is made again. */
	private final Map<String, Map<String, String>> spawnedLook = new HashMap<>();
	/** Objects a player is performing right now; the player stands in for their actors. */
	private final Set<String> hidden = new HashSet<>();

	private final SceneDrops drops;
	/** Game time each actor was spawned, and actors whose item use waits for clients to have their equipment. */
	private final Map<String, Long> spawnedAt = new HashMap<>();
	private final Set<String> pendingUse = new HashSet<>();

	public ActorController(ServerLevel level, SceneDrops drops) {
		this.level = level;
		this.drops = drops;
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

	public void setHidden(Set<String> objectIds) {
		hidden.clear();
		hidden.addAll(objectIds);
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
		Map<String, ObjectState> states = new HashMap<>();
		for (SceneObject o : evaluator.scene().objects()) {
			seen.add(o.id());
			ObjectState s = evaluator.evaluate(o, tick);
			states.put(o.id(), s);
			boolean existedBefore = s.exists() ? !existed.add(o.id()) : existed.remove(o.id());
			boolean gone = s.dead() && s.ticksDead() >= DEATH_ANIMATION_TICKS;
			boolean visible = s.exists() && !gone && !hidden.contains(o.id());
			Entity e = actors.get(o.id());

			if (e != null && (e.isRemoved() || !typeMatches(e, o) || !o.appearance().equals(spawnedLook.get(o.id())))) {
				discard(o.id());
				e = null;
				changed = true;
			}
			if (!visible) {
				if (e != null) {
					if (gone && !jump) {
						level.broadcastEntityEvent(e, POOF_EVENT);
					}
					if (!jump && existedBefore && !s.exists() && "poof".equals(o.appearance().get("despawn_effect"))) {
						level.broadcastEntityEvent(e, POOF_EVENT);
					}
					if (!jump && !s.dead() && BREAKS_ON_IMPACT.contains(o.entityType())) {
						// Snowballs, eggs and potions break into item particles where they land.
						level.broadcastEntityEvent(e, (byte) 3);
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
			// A new entity always snaps into place, but only this object's; the others still glide.
			boolean snap = jump;
			boolean spawnedNow = false;
			if (e == null) {
				e = spawn(o, s);
				if (e == null) {
					continue;
				}
				changed = true;
				spawnedNow = !jump && !existedBefore;
				snap = true;
			}
			if (ActorApplier.apply(level, e, s, snap, settled(o.id()))) {
				pendingUse.add(o.id());
			} else {
				pendingUse.remove(o.id());
			}
			typeSpecific(evaluator, o, e, s, tick);
			if (spawnedNow && "poof".equals(o.appearance().get("spawn_effect"))) {
				level.broadcastEntityEvent(e, POOF_EVENT);
			}
			if (!jump && s.dead() && !wasDead.getOrDefault(o.id(), false) && "true".equals(o.appearance().get("death_drops"))
					&& e instanceof net.minecraft.world.entity.LivingEntity living) {
				// Vanilla death loot, taken over by the scene so it goes away again on rewind.
				drops.capture(level, living.getBoundingBox().inflate(3), () -> ((LivingEntityInvoker) living)
						.scenescripter$dropAllDeathLoot(level, level.damageSources().generic()));
			}
			wasDead.put(o.id(), s.dead());
			if (!snap) {
				ambientEffects(o, e, s, tick);
			}
		}
		ride(states);
		existed.retainAll(seen);
		for (Iterator<String> it = actors.keySet().iterator(); it.hasNext(); ) {
			String id = it.next();
			if (!seen.contains(id)) {
				actors.get(id).discard();
				it.remove();
				wasDead.remove(id);
				spawnedLook.remove(id);
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * State vanilla keeps in type-specific fields: a TNT's fuse, a creeper's swell, an arrow's flight, a crystal's
	 * base plate.
	 */
	private static void typeSpecific(SceneEvaluator evaluator, SceneObject o, Entity e, ObjectState s, int tick) {
		if (e instanceof PrimedTnt tnt) {
			int explodes = explodeTick(o, tick);
			tnt.setFuse(explodes < 0 ? 80 : Math.max(1, explodes - tick));
		} else if (e instanceof Creeper creeper) {
			creeper.setSwellDir(ignited(o, tick) ? 1 : -1);
			creeper.getEntityData().set(io.github.firestormfmd.scenescripter.mixin.CreeperAccessor.scenescripter$poweredData(),
					"true".equals(o.appearance().get("powered")));
		} else if (e instanceof EndCrystal crystal) {
			crystal.setShowBottom(!"false".equals(o.appearance().get("base")));
		}
		if (e instanceof Projectile) {
			ObjectState next = evaluator.evaluate(o, tick + 1);
			io.github.firestormfmd.scenescripter.core.math.Vec3 v = next.exists()
					? next.position().subtract(s.position()) : io.github.firestormfmd.scenescripter.core.math.Vec3.ZERO;
			boolean flying = v.length() > 1.0e-4;
			e.setDeltaMovement(new net.minecraft.world.phys.Vec3(v.x(), v.y(), v.z()));
			if (e instanceof AbstractArrow arrow) {
				((AbstractArrowAccessor) arrow).scenescripter$setInGround(!flying && tick > o.spawnTick());
				arrow.setCritArrow(flying && "true".equals(o.appearance().get("crit")));
			}
		}
	}

	/** Tick the object's next explosion plays, or -1. */
	private static int explodeTick(SceneObject o, int tick) {
		int best = -1;
		for (SceneEvent ev : o.events()) {
			if (ev.type().equals("explode") && ev.tick() >= tick && (best < 0 || ev.tick() < best)) {
				best = ev.tick();
			}
		}
		return best;
	}

	/**
	 * What a falling block, block or item display, dropped item or text display shows, from the object's
	 * appearance. Set before the entity is added, since a falling block's look only travels in its spawn packet.
	 */
	private void applyContent(SceneObject o, Entity e) {
		String block = o.appearance().getOrDefault("block", "").trim();
		if (!block.isEmpty() && (e instanceof FallingBlockEntity || e instanceof Display.BlockDisplay)) {
			try {
				BlockState state = BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),
						block, false).blockState();
				if (e instanceof FallingBlockEntity falling) {
					((FallingBlockAccessor) falling).scenescripter$setBlockState(state);
				} else {
					((Display.BlockDisplay) e).setBlockState(state);
				}
			} catch (CommandSyntaxException ex) {
				SceneScripter.LOGGER.warn("Scene object {} has an unreadable block {}", o.id(), block);
			}
		}
		String item = o.appearance().getOrDefault("item", "").trim();
		if (!item.isEmpty()) {
			if (e instanceof ItemEntity dropped) {
				dropped.setItem(ActorApplier.stack(item));
			} else if (e instanceof Display.ItemDisplay display) {
				display.setItemStack(ActorApplier.stack(item));
			}
		}
		if (e instanceof Display.TextDisplay text) {
			text.setText(Component.literal(o.appearance().getOrDefault("text", o.name())));
		}
		if (e instanceof Display display) {
			String billboard = o.appearance().getOrDefault("billboard", e instanceof Display.TextDisplay ? "center" : "fixed");
			try {
				display.setBillboardConstraints(Display.BillboardConstraints.valueOf(billboard.toUpperCase(java.util.Locale.ROOT)));
			} catch (IllegalArgumentException ignored) {
				// keep the default for a value from a newer version
			}
		}
	}

	private static boolean ignited(SceneObject o, int tick) {
		return o.channel(BuiltInChannels.IGNITED.name()).map(ch -> Boolean.TRUE.equals(ch.valueAt(tick))).orElse(false);
	}

	/**
	 * Seats riders on their vehicles. Vanilla positions passengers while ticking them, which actors never do, so the
	 * vehicle places each rider here after both have been moved.
	 */
	private void ride(Map<String, ObjectState> states) {
		for (Map.Entry<String, Entity> entry : actors.entrySet()) {
			Entity rider = entry.getValue();
			ObjectState s = states.get(entry.getKey());
			if (s == null) {
				continue;
			}
			Entity vehicle = s.vehicle().isEmpty() ? null : actors.get(s.vehicle());
			if (vehicle == null || vehicle == rider) {
				if (rider.isPassenger()) {
					Actors.runAsScene(rider::stopRiding);
				}
				continue;
			}
			if (rider.getVehicle() != vehicle) {
				Actors.runAsScene(() -> {
					rider.stopRiding();
					rider.startRiding(vehicle, true, false);
				});
			}
			if (rider.getVehicle() == vehicle) {
				vehicle.positionRider(rider);
			}
		}
	}

	/** Whether clients have had the actor long enough to have received its equipment. */
	private boolean settled(String objectId) {
		Long at = spawnedAt.get(objectId);
		return at != null && level.getGameTime() - at >= 2;
	}

	/**
	 * Called every server tick, also while paused: starts the item uses that were waiting for clients to receive
	 * the actor's equipment.
	 */
	public void settle(SceneEvaluator evaluator, int tick) {
		if (pendingUse.isEmpty()) {
			return;
		}
		for (String id : List.copyOf(pendingUse)) {
			Entity e = actors.get(id);
			SceneObject o = evaluator.scene().object(id).orElse(null);
			if (e == null || o == null) {
				pendingUse.remove(id);
				continue;
			}
			if (!ActorApplier.apply(level, e, evaluator.evaluate(o, tick), false, settled(id))) {
				pendingUse.remove(id);
			}
		}
	}

	/** Removes every actor, for closing a scene or stopping the server. */
	public void removeAll() {
		actors.values().forEach(Entity::discard);
		actors.clear();
		spawnedLook.clear();
		wasDead.clear();
		existed.clear();
		spawnedAt.clear();
		pendingUse.clear();
		stepDistance.clear();
	}

	private void discard(String objectId) {
		spawnedLook.remove(objectId);
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
		if (e instanceof Mob mob && "true".equals(o.appearance().get("baby"))) {
			mob.setBaby(true);
		}
		applyContent(o, e);
		if (e instanceof Mannequin mannequin) {
			MannequinAccessor access = (MannequinAccessor) mannequin;
			String skin = o.appearance().getOrDefault("skin", "");
			String texture = o.appearance().getOrDefault("skin_texture", "");
			String model = o.appearance().getOrDefault("model", "");
			if (!texture.isBlank() || !model.isBlank()) {
				// A skin from a resource pack texture (assets/<namespace>/textures/<path>.png), optionally with a name
				// and the slim or wide arm model.
				com.google.gson.JsonObject profile = new com.google.gson.JsonObject();
				if (!skin.isBlank()) {
					profile.addProperty("name", skin);
				}
				if (!texture.isBlank()) {
					profile.addProperty("texture", texture.trim());
				}
				if (!model.isBlank()) {
					profile.addProperty("model", model.trim());
				}
				ResolvableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, profile).resultOrPartial(
						err -> SceneScripter.LOGGER.warn("Bad skin for {}: {}", o.id(), err))
						.ifPresent(access::scenescripter$setProfile);
			} else if (!skin.isBlank()) {
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
		spawnedAt.put(o.id(), level.getGameTime());
		spawnedLook.put(o.id(), Map.copyOf(o.appearance()));
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
