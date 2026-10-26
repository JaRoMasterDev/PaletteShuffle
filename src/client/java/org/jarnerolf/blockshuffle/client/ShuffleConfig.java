package org.jarnerolf.blockshuffle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which hotbar slots the shuffle may pick and how likely each one is. Persisted in
 * {@code config/blockshuffle.json}.
 */
public final class ShuffleConfig {

    public static final int MAX_WEIGHT = 999;

    private static final Logger LOGGER = LoggerFactory.getLogger("BlockShuffle");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("blockshuffle.json");

    private static ShuffleConfig instance = new ShuffleConfig();

    /** In automatic mode every slot holding a block is equally likely; in manual mode the fields below apply. */
    private boolean manual;
    private boolean[] selected = new boolean[Inventory.getSelectionSize()];
    private int[] weights = new int[Inventory.getSelectionSize()];

    private ShuffleConfig() {
        Arrays.fill(selected, true);
        Arrays.fill(weights, 1);
    }

    public static ShuffleConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            ShuffleConfig loaded = GSON.fromJson(reader, ShuffleConfig.class);
            if (loaded != null) {
                instance = loaded.sanitized();
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Could not read {}, using default shuffle settings", PATH, e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save shuffle settings to {}", PATH, e);
        }
    }

    /**
     * The weight with which the shuffle picks this slot, assuming it holds a placeable block.
     */
    public int effectiveWeight(int slot) {
        if (!manual) {
            return 1;
        }
        return selected[slot] ? weights[slot] : 0;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }

    public boolean isSelected(int slot) {
        return selected[slot];
    }

    public void setSelected(int slot, boolean selected) {
        this.selected[slot] = selected;
    }

    public int getWeight(int slot) {
        return weights[slot];
    }

    public void setWeight(int slot, int weight) {
        weights[slot] = Math.clamp(weight, 0, MAX_WEIGHT);
    }

    /** Repairs a hand-edited or outdated file instead of failing on it. */
    private ShuffleConfig sanitized() {
        ShuffleConfig defaults = new ShuffleConfig();
        defaults.manual = manual;
        for (int slot = 0; slot < defaults.selected.length; slot++) {
            if (selected != null && slot < selected.length) {
                defaults.selected[slot] = selected[slot];
            }
            if (weights != null && slot < weights.length) {
                defaults.setWeight(slot, weights[slot]);
            }
        }
        return defaults;
    }
}
