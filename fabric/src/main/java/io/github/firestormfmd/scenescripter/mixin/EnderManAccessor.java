package io.github.firestormfmd.scenescripter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.EnderMan;

/** Lets an enderman actor scream with its jaw open without anyone looking at it. */
@Mixin(EnderMan.class)
public interface EnderManAccessor {
	@Accessor("DATA_CREEPY")
	static EntityDataAccessor<Boolean> scenescripter$creepyData() {
		throw new AssertionError();
	}
}
