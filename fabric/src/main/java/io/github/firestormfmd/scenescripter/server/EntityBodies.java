package io.github.firestormfmd.scenescripter.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import io.github.firestormfmd.scenescripter.core.path.BodySettings;
import io.github.firestormfmd.scenescripter.core.path.JumpPhysics;
import io.github.firestormfmd.scenescripter.core.runtime.BodyProvider;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Sizes and movement limits of entity types, read from the type's dimensions and default attributes.
 */
public final class EntityBodies implements BodyProvider {
	public static final EntityBodies INSTANCE = new EntityBodies();

	private final Map<String, BodySettings> cache = new HashMap<>();

	private EntityBodies() {
	}

	public static Optional<EntityType<?>> type(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return Optional.empty();
		}
		return BuiltInRegistries.ENTITY_TYPE.get(identifier).map(ref -> ref.value());
	}

	@Override
	public synchronized BodySettings bodyFor(SceneObject object) {
		return cache.computeIfAbsent(object.entityType(), EntityBodies::compute);
	}

	@SuppressWarnings("unchecked")
	private static BodySettings compute(String typeId) {
		Optional<EntityType<?>> type = type(typeId);
		if (type.isEmpty()) {
			return BodySettings.PLAYER;
		}
		EntityDimensions dims = type.get().getDimensions();
		double step = 0.6;
		double jump = JumpPhysics.VANILLA_JUMP_VELOCITY;
		if (DefaultAttributes.hasSupplier(type.get())) {
			AttributeSupplier attributes = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type.get());
			if (attributes.hasAttribute(Attributes.STEP_HEIGHT)) {
				step = attributes.getValue(Attributes.STEP_HEIGHT);
			}
			if (attributes.hasAttribute(Attributes.JUMP_STRENGTH)) {
				jump = Math.max(0.05, attributes.getValue(Attributes.JUMP_STRENGTH));
			}
		}
		double width = Math.max(0.05, dims.width());
		double height = Math.max(0.05, dims.height());
		return new BodySettings(width, height, step, jump, 45);
	}
}
