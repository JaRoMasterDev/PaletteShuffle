package org.jarnerolf.blockshuffle.client;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side state of the shuffle mode: when enabled, the selected hotbar slot
 * jumps to a random block after every block the player places.
 */
public final class ShuffleMode {

    private static boolean enabled;
    private static boolean shufflePending;

    private ShuffleMode() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether the shuffle can pick a hotbar slot holding this stack.
     */
    public static boolean isShuffleCandidate(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    public static void toggle(LocalPlayer player) {
        enabled = !enabled;
        shufflePending = false;

        if (!enabled) {
            player.sendOverlayMessage(Component.translatable("message.blockshuffle.disabled")
                    .withStyle(ChatFormatting.RED));
        } else if (selectRandomBlock(player)) {
            player.sendOverlayMessage(Component.translatable("message.blockshuffle.enabled")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            player.sendOverlayMessage(Component.translatable("message.blockshuffle.enabled_no_blocks")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * Turns shuffle mode off without feedback, so it never carries over into another world or server.
     */
    public static void reset() {
        enabled = false;
        shufflePending = false;
    }

    /**
     * Called when the local player tries to place a block with their main hand. The actual slot
     * change is deferred so the ongoing use-item flow is not disturbed: it happens at the end of
     * the tick, or before the next right-click if that comes first.
     */
    public static void onPlacementAttempt() {
        if (enabled) {
            shufflePending = true;
        }
    }

    public static void applyPendingShuffle(LocalPlayer player) {
        if (shufflePending) {
            shufflePending = false;
            selectRandomBlock(player);
        }
    }

    /**
     * Selects a random hotbar slot holding a placeable block, each such slot being equally likely
     * (including the current one). Returns false if the hotbar contains no blocks.
     */
    private static boolean selectRandomBlock(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Reservoir sampling: the n-th block slot found replaces the pick with probability 1/n.
        int pickedSlot = -1;
        int blockSlotCount = 0;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (isShuffleCandidate(inventory.getItem(slot)) && random.nextInt(++blockSlotCount) == 0) {
                pickedSlot = slot;
            }
        }

        if (pickedSlot < 0) {
            return false;
        }
        inventory.setSelectedSlot(pickedSlot);
        return true;
    }
}
