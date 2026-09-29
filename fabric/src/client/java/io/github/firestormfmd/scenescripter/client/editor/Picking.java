package io.github.firestormfmd.scenescripter.client.editor;

import java.util.Optional;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.firestormfmd.scenescripter.client.ClientScene;

/**
 * Turns the mouse position into a ray through the world, for selecting actors and placing things on the ground.
 * The editor picks actors with its own raycast because vanilla targeting ignores them.
 */
public final class Picking {
	private static final double REACH = 256;

	private Picking() {
	}

	public record Ray(Vec3 from, Vec3 direction) {
		public Vec3 at(double distance) {
			return from.add(direction.scale(distance));
		}
	}

	public static Ray ray(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		double yaw = Math.toRadians(camera.yRot());
		double pitch = Math.toRadians(camera.xRot());
		Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = right.cross(forward).normalize();
		double fov = Math.toRadians(mc.options.fov().get());
		double tan = Math.tan(fov / 2);
		double aspect = (double) screenWidth / Math.max(1, screenHeight);
		double nx = (2 * mouseX / screenWidth - 1) * tan * aspect;
		double ny = (1 - 2 * mouseY / screenHeight) * tan;
		Vec3 dir = forward.add(right.scale(nx)).add(up.scale(ny)).normalize();
		return new Ray(camera.position(), dir);
	}

	/** The actor under the ray, nearest first. */
	public static Optional<String> pickActor(Ray ray) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return Optional.empty();
		}
		Vec3 to = ray.at(REACH);
		String best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : mc.level.entitiesForRendering()) {
			String objectId = ClientScene.objectOf(e);
			if (objectId == null) {
				continue;
			}
			AABB box = e.getBoundingBox().inflate(0.1);
			Optional<Vec3> hit = box.clip(ray.from(), to);
			if (box.contains(ray.from())) {
				hit = Optional.of(ray.from());
			}
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(ray.from());
				if (d < bestDist) {
					bestDist = d;
					best = objectId;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** Where the ray meets a block, if it does. */
	public static Optional<BlockHitResult> pickBlock(Ray ray) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return Optional.empty();
		}
		BlockHitResult hit = mc.level.clip(new ClipContext(ray.from(), ray.at(REACH), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, mc.player));
		return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit) : Optional.empty();
	}
}
