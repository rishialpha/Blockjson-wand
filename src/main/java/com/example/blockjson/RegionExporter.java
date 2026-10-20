package com.example.blockjson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Reads every block in a box and turns it into JSON. */
final class RegionExporter {
	/** Refuse selections bigger than this so a huge box can't freeze the server. */
	static final long MAX_VOLUME = 200_000L;

	/** Set to true to include air blocks in the export. */
	static final boolean INCLUDE_AIR = false;

	/** Regions with at most this many blocks are also printed straight into chat. */
	static final int MAX_CHAT_BLOCKS = 24;

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/**
	 * @param chatJson JSON to show in chat, or null when the region is too big for chat
	 * @param file     the timestamped file that was written
	 */
	record Result(long volume, int blockCount, int paletteSize, String chatJson, Path file) {
	}

	static Result export(Level level, BlockPos a, BlockPos b, Path outDir) throws IOException {
		int minX = Math.min(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxX = Math.max(a.getX(), b.getX());
		int maxY = Math.max(a.getY(), b.getY());
		int maxZ = Math.max(a.getZ(), b.getZ());
		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;

		long volume = (long) sizeX * sizeY * sizeZ;
		if (volume > MAX_VOLUME) {
			throw new IllegalArgumentException("Selection is " + volume + " blocks; the limit is " + MAX_VOLUME + ".");
		}

		// Distinct block states -> palette index (in first-seen order).
		Map<BlockState, Integer> palette = new LinkedHashMap<>();
		// Each entry: x, y, z (relative to the min corner), palette index.
		List<int[]> blocks = new ArrayList<>();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (!INCLUDE_AIR && state.isAir()) {
						continue;
					}
					Integer index = palette.get(state);
					if (index == null) {
						index = palette.size();
						palette.put(state, index);
					}
					blocks.add(new int[] {x - minX, y - minY, z - minZ, index});
				}
			}
		}

		List<JsonObject> paletteJson = new ArrayList<>();
		for (BlockState state : palette.keySet()) {
			paletteJson.add(stateToJson(state));
		}

		String fileText = buildFileText(a, b, minX, minY, minZ, sizeX, sizeY, sizeZ, paletteJson, blocks);

		Files.createDirectories(outDir);
		Path file = outDir.resolve("export-" + STAMP.format(LocalDateTime.now()) + ".json");
		Files.writeString(file, fileText, StandardCharsets.UTF_8);
		Files.writeString(outDir.resolve("latest.json"), fileText, StandardCharsets.UTF_8);

		String chatJson = null;
		if (volume == 1) {
			// A single block: show just its block state JSON, e.g. {"Name":"minecraft:oak_stairs","Properties":{...}}
			chatJson = GSON.toJson(stateToJson(level.getBlockState(a)));
		} else if (blocks.size() <= MAX_CHAT_BLOCKS) {
			chatJson = buildChatJson(minX, minY, minZ, sizeX, sizeY, sizeZ, paletteJson, blocks);
		}

		return new Result(volume, blocks.size(), palette.size(), chatJson, file);
	}

	/** {"Name": "minecraft:oak_stairs", "Properties": {"facing": "north", ...}} - same shape the game itself uses. */
	static JsonObject stateToJson(BlockState state) {
		JsonObject json = new JsonObject();
		json.addProperty("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		if (!state.getProperties().isEmpty()) {
			JsonObject properties = new JsonObject();
			for (Property<?> property : state.getProperties()) {
				properties.addProperty(property.getName(), valueName(state, property));
			}
			json.add("Properties", properties);
		}
		return json;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static String triple(int x, int y, int z) {
		return "[" + x + ", " + y + ", " + z + "]";
	}

	/** Readable file: one palette entry per line and one block per line. */
	private static String buildFileText(BlockPos a, BlockPos b, int minX, int minY, int minZ,
										int sizeX, int sizeY, int sizeZ,
										List<JsonObject> paletteJson, List<int[]> blocks) {
		StringBuilder sb = new StringBuilder(256 + blocks.size() * 40);
		sb.append("{\n");
		sb.append("  \"pos1\": ").append(triple(a.getX(), a.getY(), a.getZ())).append(",\n");
		sb.append("  \"pos2\": ").append(triple(b.getX(), b.getY(), b.getZ())).append(",\n");
		sb.append("  \"min\": ").append(triple(minX, minY, minZ)).append(",\n");
		sb.append("  \"size\": ").append(triple(sizeX, sizeY, sizeZ)).append(",\n");

		sb.append("  \"palette\": [\n");
		for (int i = 0; i < paletteJson.size(); i++) {
			sb.append("    ").append(GSON.toJson(paletteJson.get(i)));
			sb.append(i < paletteJson.size() - 1 ? ",\n" : "\n");
		}
		sb.append("  ],\n");

		sb.append("  \"blocks\": [\n");
		for (int i = 0; i < blocks.size(); i++) {
			int[] block = blocks.get(i);
			sb.append("    {\"pos\": ").append(triple(block[0], block[1], block[2]));
			sb.append(", \"state\": ").append(block[3]).append("}");
			sb.append(i < blocks.size() - 1 ? ",\n" : "\n");
		}
		sb.append("  ]\n");
		sb.append("}\n");
		return sb.toString();
	}

	/** Compact one-line version for chat. */
	private static String buildChatJson(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
										List<JsonObject> paletteJson, List<int[]> blocks) {
		JsonObject root = new JsonObject();
		root.add("min", intArray(minX, minY, minZ));
		root.add("size", intArray(sizeX, sizeY, sizeZ));

		JsonArray palette = new JsonArray();
		for (JsonObject entry : paletteJson) {
			palette.add(entry);
		}
		root.add("palette", palette);

		JsonArray blockArray = new JsonArray();
		for (int[] block : blocks) {
			JsonObject entry = new JsonObject();
			entry.add("pos", intArray(block[0], block[1], block[2]));
			entry.addProperty("state", block[3]);
			blockArray.add(entry);
		}
		root.add("blocks", blockArray);
		return GSON.toJson(root);
	}

	private static JsonArray intArray(int x, int y, int z) {
		JsonArray array = new JsonArray();
		array.add(x);
		array.add(y);
		array.add(z);
		return array;
	}
}
