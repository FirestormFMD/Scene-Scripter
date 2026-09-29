package io.github.firestormfmd.scenescripter.server;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.firestormfmd.scenescripter.core.scene.ExplosionRules;
import io.github.firestormfmd.scenescripter.core.scene.InteractionRules;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.core.solve.Ballistics;
import io.github.firestormfmd.scenescripter.core.solve.Solver;

/**
 * Plays the one-off parts of events as the playhead passes them: arm swings, hurt flashes, explosion blasts, shot
 * and block sounds. Lasting results (health, death, position, blocks) come from the timeline, so scrubbing never
 * depends on events having fired.
 */
public final class EventPlayer {
	/** Lets vanilla draw and sound an explosion without touching blocks or entities; the scene does that itself. */
	private static final ExplosionDamageCalculator EFFECTS_ONLY = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return false;
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return 0;
		}
	};

	/** Sounds vanilla plays when each kind of projectile is fired. */
	private static final Map<String, String> SHOT_SOUNDS = Map.of(
			"minecraft:arrow", "entity.arrow.shoot",
			"minecraft:spectral_arrow", "entity.arrow.shoot",
			"minecraft:trident", "item.trident.throw",
			"minecraft:snowball", "entity.snowball.throw",
			"minecraft:egg", "entity.egg.throw",
			"minecraft:splash_potion", "entity.splash_potion.throw",
			"minecraft:lingering_potion", "entity.lingering_potion.throw",
			"minecraft:fireball", "entity.ghast.shoot",
			"minecraft:small_fireball", "entity.blaze.shoot",
			"minecraft:wind_charge", "entity.wind_charge.throw");

	private final SceneSession session;

	public EventPlayer(SceneSession session) {
		this.session = session;
	}

	public void fire(SceneObject owner, SceneEvent event) {
		Entity actor = session.actors().actor(owner.id()).orElse(null);
		switch (event.type()) {
			case "attack", "swing" -> swing(actor, event);
			case "hurt" -> {
				if (actor != null) {
					session.level().broadcastDamageEvent(actor, session.level().damageSources().generic());
				}
			}
			case "explode" -> explode(owner, event);
			case "shoot" -> shoot(owner, event, actor);
			case "place_block", "break_block", "use_block" -> {
				swing(actor, event);
				block(event);
			}
			case "sound" -> sound(owner, event, actor);
			default -> {
			}
		}
	}

	private static void swing(Entity actor, SceneEvent event) {
		if (actor instanceof LivingEntity living) {
			boolean offhand = "off".equals(event.params().get("hand"));
			living.swing(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
		}
	}

	private void explode(SceneObject owner, SceneEvent event) {
		Optional<Solver.ExplosionResult> result = session.explosion(event.id());
		if (result.isEmpty()) {
			return;
		}
		Solver.ExplosionResult r = result.get();
		ExplosionRules rules = owner.rules().withFallback(session.scene().settings().rules())
				.withFallback(InteractionRules.DEFAULTS).explosions();
		ExplosionDamageCalculator calculator = Boolean.TRUE.equals(rules.damageRealEntities()) ? null : EFFECTS_ONLY;
		// Scene actors are invulnerable, so only real entities can be hurt when that rule is on.
		session.level().explode(null, null, calculator, r.center().x(), r.center().y(), r.center().z(), r.power(),
				false, Level.ExplosionInteraction.NONE);
	}

	private void shoot(SceneObject owner, SceneEvent event, Entity actor) {
		String projectile = event.params().get("projectile") instanceof String p ? p : null;
		if (projectile == null) {
			projectile = session.scene().object(event.id() + ":projectile").map(SceneObject::entityType)
					.orElse(Ballistics.ARROW.entityType());
		}
		String sound = SHOT_SOUNDS.get(projectile);
		if (sound != null && actor != null) {
			playSound(actor.getX(), actor.getY(), actor.getZ(), sound, SoundSource.HOSTILE, 1.0f,
					1.0f / (session.level().getRandom().nextFloat() * 0.4f + 1.2f) + 0.5f);
		}
		swing(actor, event);
	}

	private void block(SceneEvent event) {
		Optional<io.github.firestormfmd.scenescripter.core.math.BlockPos> at = Solver.blockPos(event.params());
		if (at.isEmpty()) {
			return;
		}
		BlockPos pos = new BlockPos(at.get().x(), at.get().y(), at.get().z());
		BlockState now = session.level().getBlockState(pos);
		switch (event.type()) {
			case "break_block" -> session.journal().replacedBy(event.id(), at.get()).ifPresent(before -> {
				if (!before.state().isAir()) {
					session.level().levelEvent(null, 2001, pos, Block.getId(before.state()));
				}
			});
			case "place_block" -> {
				SoundType type = now.getSoundType();
				session.level().playSound(null, pos, type.getPlaceSound(), SoundSource.BLOCKS,
						(type.getVolume() + 1.0f) / 2.0f, type.getPitch() * 0.8f);
			}
			case "use_block" -> {
				SoundType type = now.getSoundType();
				session.level().playSound(null, pos, type.getHitSound(), SoundSource.BLOCKS, 0.5f, 1.2f);
				BlockEntity be = session.level().getBlockEntity(pos);
				if (be != null && event.params().get("open") instanceof Boolean open) {
					// Chest-like lids open and close through block events.
					session.level().blockEvent(pos, now.getBlock(), 1, open ? 1 : 0);
				}
			}
			default -> {
			}
		}
	}

	/** A sound event: {@code sound} is a sound ID, played at the object or at {@code x y z}. */
	private void sound(SceneObject owner, SceneEvent event, Entity actor) {
		if (!(event.params().get("sound") instanceof String id)) {
			return;
		}
		float volume = event.params().get("volume") instanceof Number n ? n.floatValue() : 1.0f;
		float pitch = event.params().get("pitch") instanceof Number n ? n.floatValue() : 1.0f;
		if (event.params().get("x") instanceof Number x && event.params().get("y") instanceof Number y
				&& event.params().get("z") instanceof Number z) {
			playSound(x.doubleValue(), y.doubleValue(), z.doubleValue(), id, SoundSource.MASTER, volume, pitch);
		} else if (actor != null) {
			playSound(actor.getX(), actor.getY(), actor.getZ(), id, SoundSource.NEUTRAL, volume, pitch);
		} else {
			// A scene-wide sound, such as music or thunder, is heard by everyone where they stand.
			for (var player : session.level().players()) {
				playSound(player.getX(), player.getY(), player.getZ(), id, SoundSource.MASTER, volume, pitch);
			}
		}
	}

	private void playSound(double x, double y, double z, String id, SoundSource source, float volume, float pitch) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return;
		}
		Optional<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.get(key).map(ref -> ref.value());
		sound.ifPresent(s -> session.level().playSound(null, x, y, z, s, source, volume, pitch));
	}
}
