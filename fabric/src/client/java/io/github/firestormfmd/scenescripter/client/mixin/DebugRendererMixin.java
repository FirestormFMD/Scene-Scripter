package io.github.firestormfmd.scenescripter.client.mixin;

import java.util.List;

import net.minecraft.client.renderer.debug.DebugRenderer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.firestormfmd.scenescripter.client.render.EditorOverlay;

/** Adds the editor's in-world overlay (paths, selection boxes, labels) to vanilla's gizmo pass. */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
	@Shadow
	@Final
	private List<DebugRenderer.SimpleDebugRenderer> renderers;

	@Inject(method = "refreshRendererList", at = @At("TAIL"))
	private void scenescripter$addEditorOverlay(CallbackInfo ci) {
		renderers.add(EditorOverlay.INSTANCE);
	}
}
