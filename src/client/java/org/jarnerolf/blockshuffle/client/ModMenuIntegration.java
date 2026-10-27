package org.jarnerolf.blockshuffle.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds a Configure button for BlockShuffle to Mod Menu. Only Mod Menu loads this class. */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShuffleSettingsScreen::new;
    }
}
