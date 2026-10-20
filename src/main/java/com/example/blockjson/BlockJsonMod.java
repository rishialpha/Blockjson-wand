package com.example.blockjson;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iron shovel = block JSON wand.
 *
 * <ul>
 *   <li>Left-click a block: set position 1</li>
 *   <li>Right-click a block: set position 2</li>
 *   <li>Right-click the air: export the JSON for every block between the two positions</li>
 * </ul>
 *
 * With REQUIRE_SNEAK on (default) all three only work while sneaking, so the shovel still digs and
 * makes paths normally the rest of the time.
 */
public class BlockJsonMod implements ModInitializer {
	public static final String MOD_ID = "blockjson";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Set to false to make the shovel act as a wand ALL the time (like WorldEdit's wand). */
	private static final boolean REQUIRE_SNEAK = true;

	private static final int EXPORT_COOLDOWN_TICKS = 10;

	private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

	@Override
	public void onInitialize() {
		AttackBlockCallback.EVENT.register(BlockJsonMod::onAttackBlock);
		UseBlockCallback.EVENT.register(BlockJsonMod::onUseBlock);
		UseItemCallback.EVENT.register(BlockJsonMod::onUseItem);
		LOGGER.info("Block JSON Wand loaded: iron shovel is now a selection wand{}", REQUIRE_SNEAK ? " (while sneaking)" : "");
	}

	private static boolean isWand(Player player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND
				&& !player.isSpectator()
				&& player.getMainHandItem().is(Items.IRON_SHOVEL)
				&& (!REQUIRE_SNEAK || player.isShiftKeyDown());
	}

	// These callbacks run on both the client and the server. On the client we return SUCCESS, which makes Fabric
	// send the click to the server (FAIL would swallow it). The server does the real work.

	private static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand,
												   BlockPos pos, Direction direction) {
		if (!isWand(player, hand)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			setPosition(serverPlayer, level, pos, true);
			return InteractionResult.FAIL; // don't break the block
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
												BlockHitResult hitResult) {
		if (!isWand(player, hand)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			setPosition(serverPlayer, level, hitResult.getBlockPos(), false);
		}
		return InteractionResult.SUCCESS; // don't make a dirt path
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (!isWand(player, hand)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			export(serverPlayer, level);
		}
		return InteractionResult.SUCCESS;
	}

	private static void setPosition(ServerPlayer player, Level level, BlockPos pos, boolean first) {
		Selection selection = SELECTIONS.computeIfAbsent(player.getUUID(), id -> new Selection());
		BlockPos fixed = pos.immutable();
		boolean changed = first
				? selection.setPos1(level.dimension(), fixed)
				: selection.setPos2(level.dimension(), fixed);
		if (!changed) {
			return; // holding the button re-fires the event; stay quiet
		}

		String text = (first ? "Position 1" : "Position 2")
				+ " set to (" + fixed.getX() + ", " + fixed.getY() + ", " + fixed.getZ() + ")";
		if (selection.isComplete()) {
			text += " - " + selection.volume() + " blocks selected. Right-click the air to export.";
		}
		player.sendSystemMessage(Component.literal(text).withStyle(first ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE));
	}

	private static void export(ServerPlayer player, Level level) {
		Selection selection = SELECTIONS.computeIfAbsent(player.getUUID(), id -> new Selection());

		long now = level.getGameTime();
		if (now - selection.lastExportTick < EXPORT_COOLDOWN_TICKS) {
			return;
		}
		selection.lastExportTick = now;

		if (!selection.isComplete()) {
			player.sendSystemMessage(Component.literal(
					"Select two corners first: left-click a block for position 1, right-click a block for position 2.")
					.withStyle(ChatFormatting.RED));
			return;
		}
		if (!level.dimension().equals(selection.dimension())) {
			player.sendSystemMessage(Component.literal("Your selection is in a different dimension.")
					.withStyle(ChatFormatting.RED));
			return;
		}

		try {
			Path gameDir = FabricLoader.getInstance().getGameDir();
			RegionExporter.Result result = RegionExporter.export(
					level, selection.pos1(), selection.pos2(), gameDir.resolve("blockjson"));

			if (result.chatJson() != null) {
				player.sendSystemMessage(Component.literal(result.chatJson()).withStyle(ChatFormatting.YELLOW));
			}
			player.sendSystemMessage(Component.literal(
					"Exported " + result.blockCount() + " blocks (" + result.paletteSize() + " block types, "
							+ result.volume() + " in the region) to " + gameDir.relativize(result.file())
							+ " (also saved as latest.json).")
					.withStyle(ChatFormatting.GREEN));
		} catch (IllegalArgumentException e) {
			player.sendSystemMessage(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
		} catch (IOException e) {
			LOGGER.error("Could not write block JSON export", e);
			player.sendSystemMessage(Component.literal("Could not write the export file; see the log.")
					.withStyle(ChatFormatting.RED));
		}
	}
}
