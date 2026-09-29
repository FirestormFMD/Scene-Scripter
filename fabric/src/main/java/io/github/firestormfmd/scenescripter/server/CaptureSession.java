package io.github.firestormfmd.scenescripter.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.firestormfmd.scenescripter.core.capture.CaptureSample;
import io.github.firestormfmd.scenescripter.core.capture.Take;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;

/**
 * One player performing an object: a countdown through the pre-roll while the scene plays up to the punch-in, then
 * a sample every tick until the punch-out. The player cannot be hurt meanwhile, and their block interactions are
 * recorded as events instead of changing the world.
 */
public final class CaptureSession {
	private static final Map<String, EquipmentSlot> SLOTS = Map.of(
			"mainhand", EquipmentSlot.MAINHAND,
			"offhand", EquipmentSlot.OFFHAND,
			"head", EquipmentSlot.HEAD,
			"chest", EquipmentSlot.CHEST,
			"legs", EquipmentSlot.LEGS,
			"feet", EquipmentSlot.FEET);

	final ServerPlayer player;
	final String objectId;
	final int punchIn;
	/** Last tick recorded, or -1 to record to the end of the scene. */
	final int punchOut;
	final int preroll;
	final boolean loop;
	private final boolean wasInvulnerable;
	private final Vec3 returnTo;
	private final float returnYaw;
	private final float returnPitch;
	private final List<CaptureSample> samples = new ArrayList<>();
	private final List<SceneEvent> events = new ArrayList<>();
	private int lastCountdown = -1;
	private int passes;

	CaptureSession(ServerPlayer player, String objectId, int punchIn, int punchOut, int preroll, boolean loop) {
		this.player = player;
		this.objectId = objectId;
		this.punchIn = punchIn;
		this.punchOut = punchOut;
		this.preroll = preroll;
		this.loop = loop;
		this.wasInvulnerable = player.isInvulnerable();
		this.returnTo = player.position();
		this.returnYaw = player.getYRot();
		this.returnPitch = player.getXRot();
	}

	/** First tick the scene plays from: the punch-in minus the pre-roll. */
	int startTick() {
		return Math.max(0, punchIn - preroll);
	}

	boolean recording(int tick) {
		return tick >= punchIn;
	}

	int passes() {
		return passes;
	}

	/** Called once per server tick with the scene's playhead. */
	void tick(int tick) {
		if (!recording(tick)) {
			int seconds = (punchIn - tick + 19) / 20;
			if (seconds != lastCountdown) {
				lastCountdown = seconds;
				player.sendOverlayMessage(Component.literal(seconds > 0 ? "Recording in " + seconds + "..." : "Action!"));
			}
			return;
		}
		if (lastCountdown != 0) {
			lastCountdown = 0;
			player.sendOverlayMessage(Component.literal("Recording - press Right Ctrl to stop"));
		}
		if (!samples.isEmpty() && samples.getLast().tick() >= tick) {
			return;
		}
		Map<String, String> equipment = new LinkedHashMap<>();
		SLOTS.forEach((name, slot) -> {
			ItemStack stack = player.getItemBySlot(slot);
			equipment.put(name, stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		});
		Vec3 p = player.position();
		samples.add(new CaptureSample(tick, new io.github.firestormfmd.scenescripter.core.math.Vec3(p.x, p.y, p.z),
				player.yBodyRot, player.getYHeadRot(), player.getXRot(), player.onGround(), player.isShiftKeyDown(),
				player.isSprinting(), player.isSwimming(), player.isShiftKeyDown() ? "standing" : player.getPose().getSerializedName(),
				equipment));
	}

	/** Records an event the performer caused at the current tick, such as an attack or a block break. */
	void event(int tick, String type, String target, Map<String, Object> params) {
		if (!recording(tick)) {
			return;
		}
		// A swing is part of an attack or block event in the same tick, so it is not recorded twice.
		if (type.equals("swing") && events.stream().anyMatch(e -> e.tick() == tick)) {
			return;
		}
		if (!type.equals("swing")) {
			events.removeIf(e -> e.tick() == tick && e.type().equals("swing"));
		}
		String id = "cap" + System.nanoTime() % 1_000_000_000L + "_" + events.size();
		events.add(new SceneEvent(id, tick, type, target, params, null));
	}

	boolean hasSamples() {
		return samples.size() >= 2;
	}

	/** The take recorded so far, and starts over for the next loop pass. */
	Take finishPass(String takeId, String name) {
		Take take = new Take(takeId, objectId, name, System.currentTimeMillis(), samples, events);
		samples.clear();
		events.clear();
		lastCountdown = -1;
		passes++;
		return take;
	}

	/** Gives the player back their vulnerability and puts them where they started. */
	void release() {
		player.setInvulnerable(wasInvulnerable);
		player.connection.teleport(returnTo.x, returnTo.y, returnTo.z, returnYaw, returnPitch);
		player.sendOverlayMessage(Component.literal("Capture finished"));
	}
}
