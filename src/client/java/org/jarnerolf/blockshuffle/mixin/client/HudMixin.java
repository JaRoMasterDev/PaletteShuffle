package org.jarnerolf.blockshuffle.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.jarnerolf.blockshuffle.client.ShuffleMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {

    // Translucent version of the green used for the "enabled" message.
    @Unique
    private static final int HIGHLIGHT_COLOR = 0x6655FF55;

    // Runs right after the hotbar background is drawn, so the selection frame and the items stay on top.
    @Inject(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void blockshuffle$highlightShuffleSlots(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!ShuffleMode.isEnabled() || player == null) {
            return;
        }

        // Each slot's 16x16 interior starts 3px into the 182x22 hotbar sprite, with slots 20px apart.
        int left = graphics.guiWidth() / 2 - 91 + 3;
        int top = graphics.guiHeight() - 22 + 3;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (ShuffleMode.isShuffleCandidate(inventory.getItem(slot))) {
                int x = left + slot * 20;
                graphics.fill(x, top, x + 16, top + 16, HIGHLIGHT_COLOR);
            }
        }
    }
}
