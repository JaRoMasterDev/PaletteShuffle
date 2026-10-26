package org.jarnerolf.blockshuffle.mixin.client;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jarnerolf.blockshuffle.client.ShuffleMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    // Fires for the client-side placement prediction; the integrated server's own call is skipped.
    @Inject(method = "place", at = @At("RETURN"))
    private void blockshuffle$onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide()
                && context.getHand() == InteractionHand.MAIN_HAND
                && cir.getReturnValue().consumesAction()) {
            ShuffleMode.onBlockPlaced();
        }
    }
}
