package io.github.firestormfmd.scenescripter.client.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.editor.EditorMode;
import io.github.firestormfmd.scenescripter.client.editor.EditorState;
import io.github.firestormfmd.scenescripter.core.path.BodySettings;
import io.github.firestormfmd.scenescripter.core.path.Locomotion;
import io.github.firestormfmd.scenescripter.core.path.LocomotionPlanner;
import io.github.firestormfmd.scenescripter.core.path.MotionSample;
import io.github.firestormfmd.scenescripter.core.path.PathIssue;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathPoint;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;
import io.github.firestormfmd.scenescripter.server.LevelTerrain;

/**
 * Draws the editor's in-world overlay with vanilla gizmos: actor boxes, motion paths and their problems, and the
 * path being drawn. Only visible while the editor is open, so it never shows up in recordings.
 */
public final class EditorOverlay implements DebugRenderer.SimpleDebugRenderer {
	public static final EditorOverlay INSTANCE = new EditorOverlay();

	private static final int ACTOR = 0x80FFFFFF;
	private static final int SELECTED = 0xFFFFD84C;
	private static final int HOVERED = 0xFF6EC8FF;
	private static final int PATH = 0xFF4C8DFF;
	private static final int PATH_SELECTED = 0xFFFFD84C;
	private static final int PROBLEM = 0xFFFF4040;
	private static final int DRAFT = 0xFF7CFF7C;
	private static final int HIT = 0xFF5ADB7A;

	private long cachedVersion = -1;
	private final Map<String, Locomotion> previews = new HashMap<>();

	private EditorOverlay() {
	}

	@Override
	public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues, Frustum frustum,
			float partialTicks) {
		if (!EditorMode.isOpen()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Scene scene = ClientScene.scene().orElse(null);
		if (scene == null || mc.level == null) {
			return;
		}
		if (cachedVersion != ClientScene.version()) {
			cachedVersion = ClientScene.version();
			previews.clear();
		}

		for (SceneObject o : scene.objects()) {
			ClientScene.actor(o.id()).ifPresent(e -> {
				int color = o.id().equals(EditorState.selectedObject) ? SELECTED
						: o.id().equals(EditorState.hoveredObject) ? HOVERED : ACTOR;
				Gizmos.cuboid(interpolatedBox(e, partialTicks), GizmoStyle.stroke(color,
						color == ACTOR ? 1.0f : 2.5f));
				if (color != ACTOR) {
					Gizmos.billboardTextOverMob(e, 0, o.name(), color, 0.6f);
				}
			});
		}

		for (MotionPath path : scene.paths()) {
			drawPath(mc, scene, path);
		}

		int tick = ClientScene.tick();
		for (ClientScene.AttackLine attack : ClientScene.attacks()) {
			if (attack.target() == null || Math.abs(attack.tick() - tick) > 20) {
				continue;
			}
			boolean related = attack.attacker().equals(EditorState.selectedObject) || attack.target().equals(EditorState.selectedObject);
			if (!related) {
				continue;
			}
			Entity from = ClientScene.actor(attack.attacker()).orElse(null);
			Entity to = ClientScene.actor(attack.target()).orElse(null);
			if (from == null || to == null) {
				continue;
			}
			Vec3 a = from.getEyePosition(partialTicks);
			Vec3 b = to.getPosition(partialTicks).add(0, to.getBbHeight() / 2, 0);
			int color = attack.hit() ? HIT : PROBLEM;
			Gizmos.arrow(a, b, color, attack.hit() ? 2.5f : 1.5f);
			String label = attack.hit()
					? String.format(java.util.Locale.ROOT, "hit @%d: %.1f", attack.tick(), attack.damage())
					: "miss @" + attack.tick() + ": " + attack.reason();
			Gizmos.billboardText(label, a.add(b).scale(0.5).add(0, 0.4, 0),
					net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(color));
		}

		if (EditorState.dragPreview != null) {
			Vec3 at = mc(EditorState.dragPreview);
			Gizmos.cuboid(new AABB(at.add(-0.3, 0, -0.3), at.add(0.3, 1.8, 0.3)), GizmoStyle.stroke(DRAFT, 2f));
		}

		List<io.github.firestormfmd.scenescripter.core.math.Vec3> draft = EditorState.pathDraft;
		for (int i = 0; i < draft.size(); i++) {
			Vec3 p = mc(draft.get(i));
			Gizmos.point(p, DRAFT, 6f);
			if (i > 0) {
				Gizmos.line(mc(draft.get(i - 1)), p, DRAFT, 2f);
			}
		}
	}

	private void drawPath(Minecraft mc, Scene scene, MotionPath path) {
		if (path.points().isEmpty()) {
			return;
		}
		boolean selected = path.id().equals(EditorState.selectedPath);
		Locomotion preview = previews.computeIfAbsent(path.id(), id -> plan(mc, scene, path));
		int color = selected ? PATH_SELECTED : PATH;
		if (preview != null) {
			List<MotionSample> samples = preview.samples();
			for (int i = 1; i < samples.size(); i++) {
				Gizmos.line(mc(samples.get(i - 1).pos()).add(0, 0.05, 0), mc(samples.get(i).pos()).add(0, 0.05, 0), color, selected ? 3f : 2f);
			}
			for (PathIssue issue : preview.issues()) {
				MotionSample s = preview.sampleAt(issue.tick());
				Vec3 at = mc(s.pos());
				Gizmos.cuboid(new AABB(at.add(-0.3, 0, -0.3), at.add(0.3, 1.8, 0.3)), GizmoStyle.stroke(PROBLEM, 2f));
				Gizmos.billboardText(issue.kind().message(), at.add(0, 2.2, 0),
						net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(PROBLEM));
			}
		}
		for (PathPoint point : path.points()) {
			Gizmos.point(mc(point.pos()).add(0, 0.1, 0), color, selected ? 8f : 5f);
		}
	}

	private static Locomotion plan(Minecraft mc, Scene scene, MotionPath path) {
		try {
			return LocomotionPlanner.plan(path, MotionClip.atSpeed(path.id(), 0), BodySettings.PLAYER,
					new LevelTerrain(mc.level, scene.settings().groundFilter()), scene.settings().groundFilter());
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static AABB interpolatedBox(Entity e, float partialTicks) {
		Vec3 delta = e.getPosition(partialTicks).subtract(e.position());
		return e.getBoundingBox().move(delta);
	}

	private static Vec3 mc(io.github.firestormfmd.scenescripter.core.math.Vec3 v) {
		return new Vec3(v.x(), v.y(), v.z());
	}
}
