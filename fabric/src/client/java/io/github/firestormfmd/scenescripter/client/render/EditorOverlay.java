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
	private static final int BLAST = 0xFFFF7A2E;
	private static final int BOUNDS = 0x90B0B0B0;
	private static final int GHOST = 0x60FFFFFF;
	private static final int SHOT = 0xFFE0E070;
	/** Most blocks outlined for the selected object's blast; bigger craters show as one box. */
	private static final int MAX_BLOCK_OUTLINES = 1500;
	/** Ghosts are drawn this many ticks apart, this far either side of the playhead. */
	private static final int GHOST_STEP = 10;
	private static final int GHOST_RANGE = 40;

	private long cachedVersion = -1;
	private final Map<String, Locomotion> previews = new HashMap<>();
	private io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator evaluator;

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
		evaluator = ClientScene.evaluator().orElse(null);
		if (evaluator == null) {
			return;
		}

		if (scene.bounds() != null) {
			var b = scene.bounds();
			Gizmos.cuboid(new AABB(b.min().x(), b.min().y(), b.min().z(), b.max().x() + 1, b.max().y() + 1, b.max().z() + 1),
					GizmoStyle.stroke(BOUNDS, 1f));
		}
		drawGhosts(scene);
		drawShots(scene);
		drawBlasts();

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

	/** Faint boxes where the selected object is shortly before and after the playhead. */
	private void drawGhosts(Scene scene) {
		SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
		if (o == null) {
			return;
		}
		int tick = ClientScene.tick();
		double w = io.github.firestormfmd.scenescripter.server.EntityBodies.INSTANCE.bodyFor(o).width() / 2;
		double h = io.github.firestormfmd.scenescripter.server.EntityBodies.INSTANCE.bodyFor(o).height();
		Vec3 last = null;
		for (int t = Math.max(0, tick - GHOST_RANGE); t <= Math.min(scene.length(), tick + GHOST_RANGE); t += 2) {
			var s = evaluator.evaluate(o, t);
			if (!s.exists()) {
				last = null;
				continue;
			}
			Vec3 p = mc(s.position());
			if (last != null) {
				Gizmos.line(last.add(0, 0.05, 0), p.add(0, 0.05, 0), GHOST, 1f);
			}
			last = p;
			if (t != tick && (t - tick) % GHOST_STEP == 0) {
				Gizmos.cuboid(new AABB(p.add(-w, 0, -w), p.add(w, h, w)), GizmoStyle.stroke(GHOST, 1f));
			}
		}
	}

	/** The flight of every shot the selected object fires. */
	private void drawShots(Scene scene) {
		String selected = EditorState.selectedObject;
		if (selected == null) {
			return;
		}
		SceneObject shooter = scene.object(selected).orElse(null);
		if (shooter == null) {
			return;
		}
		for (var e : shooter.events()) {
			if (!e.type().equals("shoot")) {
				continue;
			}
			scene.object(e.id() + ":projectile").ifPresent(p -> p.channel(
					io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels.POSITION.name()).ifPresent(ch -> {
				Vec3 last = null;
				for (var k : ch.keys()) {
					Vec3 at = mc((io.github.firestormfmd.scenescripter.core.math.Vec3) k.value());
					if (last != null) {
						Gizmos.line(last, at, SHOT, 1.5f);
					}
					last = at;
				}
			}));
		}
	}

	/** What upcoming explosions will break: every block for the selected object's, an outline for the rest. */
	private void drawBlasts() {
		int tick = ClientScene.tick();
		for (ClientScene.Blast b : ClientScene.explosions()) {
			boolean mine = b.owner().equals(EditorState.selectedObject)
					|| (EditorState.selectedObject != null && b.eventId().startsWith(EditorState.selectedObject + ":"));
			if (!mine && (b.tick() < tick || b.tick() > tick + 200)) {
				continue;
			}
			Vec3 c = mc(b.center());
			Gizmos.billboardText("boom @" + b.tick() + " (" + b.power() + ")", c.add(0, 1.2, 0),
					net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(BLAST));
			int[] blocks = b.blocks();
			if (blocks.length == 0) {
				continue;
			}
			if (mine && blocks.length / 3 <= MAX_BLOCK_OUTLINES) {
				for (int i = 0; i < blocks.length; i += 3) {
					Gizmos.cuboid(new AABB(blocks[i], blocks[i + 1], blocks[i + 2], blocks[i] + 1, blocks[i + 1] + 1,
							blocks[i + 2] + 1).deflate(0.02), GizmoStyle.stroke(BLAST, 1f));
				}
			} else {
				int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
				int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
				for (int i = 0; i < blocks.length; i += 3) {
					minX = Math.min(minX, blocks[i]);
					minY = Math.min(minY, blocks[i + 1]);
					minZ = Math.min(minZ, blocks[i + 2]);
					maxX = Math.max(maxX, blocks[i]);
					maxY = Math.max(maxY, blocks[i + 1]);
					maxZ = Math.max(maxZ, blocks[i + 2]);
				}
				Gizmos.cuboid(new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1), GizmoStyle.stroke(BLAST, 1.5f));
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
