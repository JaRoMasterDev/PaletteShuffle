package org.jarnerolf.blockshuffle.client;

import java.util.Arrays;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/**
 * While shuffle mode is enabled and the slots it can pick are not all equally likely, shows each
 * slot's chance right above it in the hotbar.
 */
public final class SlotChanceHud {

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    // Dark tag behind each label, so neighbors stay apart and the text reads well on the experience bar.
    private static final int BACKGROUND_COLOR = 0xB0000000;

    private SlotChanceHud() {
    }

    public static void register() {
        // Last, so the labels stay readable on top of the experience bar. Attaching to a vanilla bar
        // instead would hide them whenever that bar is hidden, e.g. the experience bar in creative.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(BlockshuffleClient.MOD_ID, "slot_chances"),
                SlotChanceHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!ShuffleMode.isEnabled() || player == null || player.isSpectator() || minecraft.gui.hud.isHidden()) {
            return;
        }

        int[] weights = ShuffleMode.slotWeights(player.getInventory());
        int[] activeWeights = Arrays.stream(weights).filter(weight -> weight > 0).toArray();
        if (Arrays.stream(activeWeights).distinct().count() < 2) {
            return;
        }

        int totalWeight = Arrays.stream(activeWeights).sum();
        Font font = minecraft.font;
        // Slot centers of the 182px wide hotbar; whole percentages so neighboring labels fit in 20px.
        int firstSlotCenter = graphics.guiWidth() / 2 - 91 + 11;
        int y = graphics.guiHeight() - 22 - font.lineHeight;
        for (int slot = 0; slot < weights.length; slot++) {
            if (weights[slot] > 0) {
                long percent = Math.round(100.0 * weights[slot] / totalWeight);
                String label = percent == 0 ? "<1%" : percent + "%";
                // The width includes 1px of letter spacing, which serves as the right padding.
                int width = font.width(label);
                int x = firstSlotCenter + slot * 20 - width / 2;
                graphics.fill(x - 1, y - 1, x + width, y + font.lineHeight - 1, BACKGROUND_COLOR);
                graphics.text(font, label, x, y, TEXT_COLOR, false);
            }
        }
    }
}
