package io.github.firestormfmd.scenescripter.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import io.github.firestormfmd.scenescripter.SceneScripter;

/**
 * Everything the editor and the server say to each other. Recordings don't depend on any of this: actors reach
 * clients through ordinary vanilla packets.
 */
public final class Payloads {
	private Payloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
		return new CustomPacketPayload.Type<>(SceneScripter.id(path));
	}

	private static void writePart(FriendlyByteBuf buf, Chunks.Part p) {
		buf.writeVarInt(p.transfer());
		buf.writeVarInt(p.index());
		buf.writeVarInt(p.count());
		buf.writeByteArray(p.data());
	}

	private static Chunks.Part readPart(FriendlyByteBuf buf) {
		return new Chunks.Part(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(Chunks.PART_SIZE + 64));
	}

	// ---- Client to server ----

	/** The editor was opened or closed. */
	public record EditorState(boolean open) implements CustomPacketPayload {
		public static final Type<EditorState> TYPE = type("editor_state");
		public static final StreamCodec<FriendlyByteBuf, EditorState> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeBoolean(p.open()), buf -> new EditorState(buf.readBoolean()));

		@Override
		public Type<EditorState> type() {
			return TYPE;
		}
	}

	/** Part of an edit op, as JSON. */
	public record Edit(Chunks.Part part) implements CustomPacketPayload {
		public static final Type<Edit> TYPE = type("edit");
		public static final StreamCodec<FriendlyByteBuf, Edit> CODEC = StreamCodec.of(
				(buf, p) -> writePart(buf, p.part()), buf -> new Edit(readPart(buf)));

		@Override
		public Type<Edit> type() {
			return TYPE;
		}
	}

	public record History(boolean redo) implements CustomPacketPayload {
		public static final Type<History> TYPE = type("history");
		public static final StreamCodec<FriendlyByteBuf, History> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeBoolean(p.redo()), buf -> new History(buf.readBoolean()));

		@Override
		public Type<History> type() {
			return TYPE;
		}
	}

	/** Play, pause, seek, speed and loop requests. */
	public record Playback(int action, int a, int b, float speed) implements CustomPacketPayload {
		public static final int PLAY = 0;
		public static final int PAUSE = 1;
		public static final int SEEK = 2;
		public static final int SPEED = 3;
		public static final int LOOP = 4;
		public static final int STOP = 5;

		public static final Type<Playback> TYPE = type("playback");
		public static final StreamCodec<FriendlyByteBuf, Playback> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.action());
					buf.writeVarInt(p.a());
					buf.writeVarInt(p.b());
					buf.writeFloat(p.speed());
				},
				buf -> new Playback(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat()));

		@Override
		public Type<Playback> type() {
			return TYPE;
		}
	}

	/** New, open, save, close, delete and list scenes. */
	public record SceneCommand(int action, String name, int length) implements CustomPacketPayload {
		public static final int NEW = 0;
		public static final int OPEN = 1;
		public static final int SAVE = 2;
		public static final int CLOSE = 3;
		public static final int LIST = 4;
		public static final int DELETE = 5;

		public static final Type<SceneCommand> TYPE = type("scene_command");
		public static final StreamCodec<FriendlyByteBuf, SceneCommand> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.action());
					buf.writeUtf(p.name(), 64);
					buf.writeVarInt(p.length());
				},
				buf -> new SceneCommand(buf.readVarInt(), buf.readUtf(64), buf.readVarInt()));

		@Override
		public Type<SceneCommand> type() {
			return TYPE;
		}
	}

	// ---- Server to client ----

	/** Part of the open scene as JSON ({@code {"name": ..., "scene": ...}}), or of an empty object when none is open. */
	public record SceneData(Chunks.Part part) implements CustomPacketPayload {
		public static final Type<SceneData> TYPE = type("scene_data");
		public static final StreamCodec<FriendlyByteBuf, SceneData> CODEC = StreamCodec.of(
				(buf, p) -> writePart(buf, p.part()), buf -> new SceneData(readPart(buf)));

		@Override
		public Type<SceneData> type() {
			return TYPE;
		}
	}

	/** Playhead and undo state of the open scene. */
	public record PlaybackState(int tick, boolean playing, float speed, int loopStart, int loopEnd, boolean dirty,
			String undoLabel, String redoLabel) implements CustomPacketPayload {
		public static final Type<PlaybackState> TYPE = type("playback_state");
		public static final StreamCodec<FriendlyByteBuf, PlaybackState> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.tick());
					buf.writeBoolean(p.playing());
					buf.writeFloat(p.speed());
					buf.writeVarInt(p.loopStart() + 1);
					buf.writeVarInt(p.loopEnd() + 1);
					buf.writeBoolean(p.dirty());
					buf.writeUtf(p.undoLabel(), 256);
					buf.writeUtf(p.redoLabel(), 256);
				},
				buf -> new PlaybackState(buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readVarInt() - 1,
						buf.readVarInt() - 1, buf.readBoolean(), buf.readUtf(256), buf.readUtf(256)));

		@Override
		public Type<PlaybackState> type() {
			return TYPE;
		}
	}

	/**
	 * Which entities are actors. Sent to every player, not just editors, so clients can apply the no-collision
	 * rules (walking through actors, not targeting them).
	 */
	public record ActorIds(List<String> objectIds, List<Integer> entityIds) implements CustomPacketPayload {
		public static final Type<ActorIds> TYPE = type("actor_ids");
		public static final StreamCodec<FriendlyByteBuf, ActorIds> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.objectIds().size());
					for (int i = 0; i < p.objectIds().size(); i++) {
						buf.writeUtf(p.objectIds().get(i), 128);
						buf.writeVarInt(p.entityIds().get(i));
					}
				},
				buf -> {
					int n = buf.readVarInt();
					List<String> objects = new ArrayList<>(n);
					List<Integer> entities = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						objects.add(buf.readUtf(128));
						entities.add(buf.readVarInt());
					}
					return new ActorIds(objects, entities);
				});

		@Override
		public Type<ActorIds> type() {
			return TYPE;
		}
	}

	public record SceneList(List<String> names) implements CustomPacketPayload {
		public static final Type<SceneList> TYPE = type("scene_list");
		public static final StreamCodec<FriendlyByteBuf, SceneList> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.names().size());
					p.names().forEach(n -> buf.writeUtf(n, 64));
				},
				buf -> {
					int n = buf.readVarInt();
					List<String> names = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						names.add(buf.readUtf(64));
					}
					return new SceneList(names);
				});

		@Override
		public Type<SceneList> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(EditorState.TYPE, EditorState.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Edit.TYPE, Edit.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(History.TYPE, History.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Playback.TYPE, Playback.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SceneCommand.TYPE, SceneCommand.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SceneData.TYPE, SceneData.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlaybackState.TYPE, PlaybackState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ActorIds.TYPE, ActorIds.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SceneList.TYPE, SceneList.CODEC);
	}
}
