package org.jarnerolf.paletteshuffle.mixin.client;

import net.minecraft.client.Minecraft;
import org.jarnerolf.paletteshuffle.client.ShuffleMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    // Several right-clicks can be processed within one tick, before the end-of-tick shuffle runs.
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void paletteshuffle$beforeUseItem(CallbackInfo ci) {
        ShuffleMode.applyPendingShuffle(((Minecraft) (Object) this).player);
    }
}
