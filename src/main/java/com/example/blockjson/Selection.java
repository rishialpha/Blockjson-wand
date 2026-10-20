package com.example.blockjson;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One player's current two-corner selection. */
final class Selection {
	private ResourceKey<Level> dimension;
	private BlockPos pos1;
	private BlockPos pos2;

	/** Game time of the last export, used to stop a held right-click from spamming. */
	long lastExportTick = -1000;

	/** @return true if the position actually changed. */
	boolean setPos1(ResourceKey<Level> dim, BlockPos pos) {
		boolean dimensionChanged = !dim.equals(dimension);
		if (dimensionChanged) {
			dimension = dim;
			pos2 = null; // the old second corner is in a different dimension
		}
		boolean changed = dimensionChanged || !pos.equals(pos1);
		pos1 = pos;
		return changed;
	}

	/** @return true if the position actually changed. */
	boolean setPos2(ResourceKey<Level> dim, BlockPos pos) {
		boolean dimensionChanged = !dim.equals(dimension);
		if (dimensionChanged) {
			dimension = dim;
			pos1 = null;
		}
		boolean changed = dimensionChanged || !pos.equals(pos2);
		pos2 = pos;
		return changed;
	}

	boolean isComplete() {
		return pos1 != null && pos2 != null;
	}

	ResourceKey<Level> dimension() {
		return dimension;
	}

	BlockPos pos1() {
		return pos1;
	}

	BlockPos pos2() {
		return pos2;
	}

	long volume() {
		long dx = Math.abs(pos1.getX() - pos2.getX()) + 1L;
		long dy = Math.abs(pos1.getY() - pos2.getY()) + 1L;
		long dz = Math.abs(pos1.getZ() - pos2.getZ()) + 1L;
		return dx * dy * dz;
	}
}
