package io.github.firestormfmd.scenescripter.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mannequin setters are private; player objects need them for skins and to hide the "NPC" label. */
@Mixin(Mannequin.class)
public interface MannequinAccessor {
	@Invoker("setProfile")
	void scenescripter$setProfile(ResolvableProfile profile);

	@Invoker("setHideDescription")
	void scenescripter$setHideDescription(boolean hide);

	@Invoker("setImmovable")
	void scenescripter$setImmovable(boolean immovable);

	@Invoker("setDescription")
	void scenescripter$setDescription(Component description);
}
