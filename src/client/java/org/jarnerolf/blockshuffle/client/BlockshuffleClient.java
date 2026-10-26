package org.jarnerolf.blockshuffle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class BlockshuffleClient implements ClientModInitializer {

    public static final String MOD_ID = "blockshuffle";

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private static KeyMapping toggleKey;
    static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {
        ShuffleConfig.load();
        SlotChanceHud.register();

        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.blockshuffle.toggle", InputConstants.KEY_K, CATEGORY));
        settingsKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.blockshuffle.settings", InputConstants.KEY_J, CATEGORY));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ShuffleMode.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ShuffleMode.reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            while (toggleKey.consumeClick()) {
                ShuffleMode.toggle(client.player);
            }
            while (settingsKey.consumeClick()) {
                client.gui.setScreen(new ShuffleSettingsScreen());
            }
            ShuffleMode.applyPendingShuffle(client.player);
        });
    }
}
