package io.github.firestormfmd.scenescripter.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import io.github.firestormfmd.scenescripter.actor.ActorAccess;

/**
 * Items the scene dropped, from blasts and deaths. They are real items that fall and settle, but nobody can pick
 * them up and they are never saved, and they go away when the playhead goes back or the scene closes.
 */
public final class SceneDrops {
	private final List<Entity> drops = new ArrayList<>();

	/** Runs something that drops items around {@code around}, and takes over whatever it dropped there. */
	public void capture(ServerLevel level, AABB around, Runnable action) {
		Set<Entity> before = new HashSet<>(level.getEntitiesOfClass(Entity.class, around, SceneDrops::isLoot));
		action.run();
		for (Entity e : level.getEntitiesOfClass(Entity.class, around, SceneDrops::isLoot)) {
			if (!before.contains(e)) {
				adopt(e);
			}
		}
	}

	/** Takes over a dropped item. Experience is removed, since picking it up would reward whoever was near. */
	public void adopt(Entity e) {
		if (e instanceof ExperienceOrb) {
			e.discard();
			return;
		}
		if (e instanceof ItemEntity item) {
			item.setNeverPickUp();
		}
		((ActorAccess) e).scenescripter$setSceneDrop(true);
		drops.add(e);
	}

	public int count() {
		drops.removeIf(Entity::isRemoved);
		return drops.size();
	}

	public void clear() {
		drops.forEach(Entity::discard);
		drops.clear();
	}

	private static boolean isLoot(Entity e) {
		return e instanceof ItemEntity || e instanceof ExperienceOrb;
	}
}
