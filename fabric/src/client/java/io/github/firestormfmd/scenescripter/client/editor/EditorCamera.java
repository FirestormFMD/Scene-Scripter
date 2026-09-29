package io.github.firestormfmd.scenescripter.client.editor;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Fly camera for the editor: hold the right mouse button to look around and fly with WASD, Space and Shift, like
 * Axiom. The player is the camera, flying in creative mode.
 */
public final class EditorCamera {
	private final Set<Integer> held = new HashSet<>();
	private boolean looking;
	private double speed = 0.5;

	public boolean isLooking() {
		return looking;
	}

	public void setLooking(boolean looking) {
		this.looking = looking;
		if (!looking) {
			held.clear();
		}
	}

	public double speed() {
		return speed;
	}

	/** Scroll while looking changes flying speed, in blocks per tick. */
	public void scroll(double amount) {
		speed = Math.clamp(speed * (amount > 0 ? 1.25 : 0.8), 0.05, 4.0);
	}

	/** @return true if the key is a camera key and was consumed */
	public boolean keyPressed(int key) {
		if (!looking || !isMovementKey(key)) {
			return false;
		}
		held.add(key);
		return true;
	}

	public void keyReleased(int key) {
		held.remove(key);
	}

	private static boolean isMovementKey(int key) {
		return key == InputConstants.KEY_W || key == InputConstants.KEY_A || key == InputConstants.KEY_S || key == InputConstants.KEY_D
				|| key == InputConstants.KEY_SPACE || key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_Q
				|| key == InputConstants.KEY_E;
	}

	public void look(double dx, double dy) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.turn(dx * 0.8, dy * 0.8);
		}
	}

	public void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (player.getAbilities().mayfly && !player.getAbilities().flying) {
			player.getAbilities().flying = true;
		}
		if (!player.getAbilities().flying) {
			return;
		}
		double forward = axis(InputConstants.KEY_W, InputConstants.KEY_S);
		double strafe = axis(InputConstants.KEY_D, InputConstants.KEY_A);
		double up = axis(InputConstants.KEY_SPACE, InputConstants.KEY_LSHIFT) + axis(InputConstants.KEY_E, InputConstants.KEY_Q);
		if (forward == 0 && strafe == 0 && up == 0) {
			if (looking) {
				player.setDeltaMovement(Vec3.ZERO);
			}
			return;
		}
		double yaw = Math.toRadians(player.getYRot());
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 v = fwd.scale(forward).add(right.scale(strafe)).add(0, up, 0);
		player.setDeltaMovement(v.normalize().scale(speed));
	}

	private double axis(int positive, int negative) {
		return (held.contains(positive) ? 1 : 0) - (held.contains(negative) ? 1 : 0);
	}
}
