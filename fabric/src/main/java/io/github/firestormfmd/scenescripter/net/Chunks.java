package io.github.firestormfmd.scenescripter.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Splits large text (scene JSON, big edits) into compressed parts small enough for custom payloads, and puts them
 * back together on the other side.
 */
public final class Chunks {
	/** Serverbound custom payloads are limited to about 32 KB, so parts stay well under that. */
	public static final int PART_SIZE = 30_000;
	private static final AtomicInteger NEXT_ID = new AtomicInteger();

	private Chunks() {
	}

	public record Part(int transfer, int index, int count, byte[] data) {
	}

	public static List<Part> split(String text) {
		byte[] zipped = gzip(text.getBytes(StandardCharsets.UTF_8));
		int transfer = NEXT_ID.incrementAndGet();
		int count = Math.max(1, (zipped.length + PART_SIZE - 1) / PART_SIZE);
		List<Part> parts = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int from = i * PART_SIZE;
			int to = Math.min(zipped.length, from + PART_SIZE);
			parts.add(new Part(transfer, i, count, Arrays.copyOfRange(zipped, from, to)));
		}
		return parts;
	}

	/** Collects parts of transfers from one sender. */
	public static final class Assembler {
		private static final int MAX_BYTES = 64 * 1024 * 1024;
		private final Map<Integer, byte[][]> pending = new HashMap<>();

		/** Adds a part; returns the full text once every part of its transfer has arrived. */
		public Optional<String> accept(Part part) {
			if (part.count() <= 0 || part.count() > MAX_BYTES / PART_SIZE || part.index() < 0 || part.index() >= part.count()) {
				return Optional.empty();
			}
			byte[][] parts = pending.computeIfAbsent(part.transfer(), t -> new byte[part.count()][]);
			if (parts.length != part.count()) {
				pending.remove(part.transfer());
				return Optional.empty();
			}
			parts[part.index()] = part.data();
			for (byte[] p : parts) {
				if (p == null) {
					return Optional.empty();
				}
			}
			pending.remove(part.transfer());
			ByteArrayOutputStream all = new ByteArrayOutputStream();
			for (byte[] p : parts) {
				all.writeBytes(p);
			}
			return Optional.of(new String(gunzip(all.toByteArray()), StandardCharsets.UTF_8));
		}
	}

	private static byte[] gzip(byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
			gz.write(data);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	private static byte[] gunzip(byte[] data) {
		try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
			return gz.readNBytes(64 * 1024 * 1024);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
