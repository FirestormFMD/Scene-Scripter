package io.github.firestormfmd.scenescripter.server;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;

import io.github.firestormfmd.scenescripter.core.journal.ChangeSet;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Plays the one-off parts of events as the playhead passes them: arm swings, hurt flashes, sounds. Lasting results
 * (health, death, position, blocks) come from the timeline, so scrubbing never depends on events having fired.
 */
public final class EventPlayer {
	private final SceneSession session;

	public EventPlayer(SceneSession session) {
		this.session = session;
	}

	public void fire(SceneObject owner, SceneEvent event) {
		Entity actor = session.actors().actor(owner.id()).orElse(null);
		if (actor == null) {
			return;
		}
		switch (event.type()) {
			case "attack", "swing" -> {
				if (actor instanceof LivingEntity living) {
					boolean offhand = "off".equals(event.params().get("hand"));
					living.swing(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
				}
			}
			case "hurt" -> session.level().broadcastDamageEvent(actor, session.level().damageSources().generic());
			default -> {
			}
		}
	}

	/** Block changes baked from events; filled in by the explosion and block-event bakers. */
	public List<ChangeSet<SavedBlock>> blockChanges() {
		return List.of();
	}
}
