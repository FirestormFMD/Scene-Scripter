package io.github.firestormfmd.scenescripter.server;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.solve.CombatModel;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Combat facts from the real game: default attributes of entity types and block raycasts for line of sight.
 */
public final class WorldCombat implements CombatModel {
	private final ServerLevel level;

	public WorldCombat(ServerLevel level) {
		this.level = level;
	}

	@SuppressWarnings("unchecked")
	private static Optional<AttributeSupplier> attributes(SceneObject o) {
		return EntityBodies.type(o.entityType())
				.filter(DefaultAttributes::hasSupplier)
				.map(t -> DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) t));
	}

	private static double attribute(SceneObject o, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			double fallback) {
		return attributes(o).filter(a -> a.hasAttribute(attr)).map(a -> a.getValue(attr)).orElse(fallback);
	}

	@Override
	public double maxHealth(SceneObject object) {
		return attribute(object, Attributes.MAX_HEALTH, 20);
	}

	@Override
	public double baseAttackDamage(SceneObject object) {
		return isPlayerLike(object) ? 1 : attribute(object, Attributes.ATTACK_DAMAGE, 2);
	}

	@Override
	public double knockbackResistance(SceneObject object) {
		return attribute(object, Attributes.KNOCKBACK_RESISTANCE, 0);
	}

	@Override
	public boolean isPlayerLike(SceneObject object) {
		return object.entityType().equals("minecraft:mannequin") || object.entityType().equals("minecraft:player");
	}

	@Override
	public double width(SceneObject object) {
		return EntityBodies.INSTANCE.bodyFor(object).width();
	}

	@Override
	public double height(SceneObject object) {
		return EntityBodies.INSTANCE.bodyFor(object).height();
	}

	@Override
	public boolean lineOfSight(Vec3 from, Vec3 to) {
		net.minecraft.world.phys.Vec3 a = new net.minecraft.world.phys.Vec3(from.x(), from.y(), from.z());
		net.minecraft.world.phys.Vec3 b = new net.minecraft.world.phys.Vec3(to.x(), to.y(), to.z());
		HitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}
}
