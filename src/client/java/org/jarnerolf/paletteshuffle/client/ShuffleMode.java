package org.jarnerolf.paletteshuffle.client;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side state of the shuffle mode: when enabled, the selected hotbar slot
 * jumps to a random block after every block the player places.
 */
public final class ShuffleMode {

    /** Note block pling pitches for the toggle sound: high when turning on, low when turning off. */
    public static final float ENABLE_PITCH = 1.5F;
    public static final float DISABLE_PITCH = 0.8F;

    private static boolean enabled;
    private static boolean shufflePending;

    private ShuffleMode() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * The weight with which the shuffle picks a hotbar slot holding this stack; 0 if it never does.
     */
    public static int slotWeight(int slot, ItemStack stack) {
        return stack.getItem() instanceof BlockItem ? ShuffleConfig.get().effectiveWeight(slot) : 0;
    }

    /** {@link #slotWeight} of every hotbar slot. */
    public static int[] slotWeights(Inventory inventory) {
        int[] weights = new int[Inventory.getSelectionSize()];
        for (int slot = 0; slot < weights.length; slot++) {
            weights[slot] = slotWeight(slot, inventory.getItem(slot));
        }
        return weights;
    }

    public static void toggle(LocalPlayer player) {
        enabled = !enabled;
        shufflePending = false;

        if (!enabled) {
            player.sendOverlayMessage(Component.translatable("message.paletteshuffle.disabled")
                    .withStyle(ChatFormatting.RED));
        } else if (selectRandomBlock(player)) {
            player.sendOverlayMessage(Component.translatable("message.paletteshuffle.enabled")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            player.sendOverlayMessage(Component.translatable("message.paletteshuffle.enabled_no_blocks")
                    .withStyle(ChatFormatting.GREEN));
        }
        // A UI sound: only this player hears it, at the UI volume.
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_PLING, enabled ? ENABLE_PITCH : DISABLE_PITCH));
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
     * Selects a random hotbar slot with probability proportional to its weight (the current slot
     * included). Returns false if no slot can be picked.
     */
    private static boolean selectRandomBlock(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Weighted reservoir sampling: a slot replaces the pick with probability weight / total weight so far.
        int pickedSlot = -1;
        int totalWeight = 0;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            int weight = slotWeight(slot, inventory.getItem(slot));
            if (weight > 0) {
                totalWeight += weight;
                if (random.nextInt(totalWeight) < weight) {
                    pickedSlot = slot;
                }
            }
        }

        if (pickedSlot < 0) {
            return false;
        }
        inventory.setSelectedSlot(pickedSlot);
        return true;
    }
}
