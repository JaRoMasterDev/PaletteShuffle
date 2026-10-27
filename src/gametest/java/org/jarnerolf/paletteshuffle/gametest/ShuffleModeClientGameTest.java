package org.jarnerolf.paletteshuffle.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jarnerolf.paletteshuffle.client.ShuffleSettingsScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Places many blocks with shuffle mode enabled and checks that every placement switches to a
 * uniformly random block slot of the hotbar.
 */
public class ShuffleModeClientGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("PaletteShuffleTest");
    private static final String[] HOTBAR_BLOCKS = {"stone", "glass", "oak_planks", "gold_block"};

    private static volatile Consumer<ServerLevel> spamRecorder;

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Consumer<ServerLevel> recorder = spamRecorder;
            if (recorder != null) {
                recorder.accept(server.overworld());
            }
        });
    }

    private ClientGameTestContext context;
    private TestServerContext server;
    private BlockPos origin;
    private BlockPos support;
    private BlockPos placePos;

    @Override
    public void runTest(ClientGameTestContext context) {
        this.context = context;
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            server = singleplayer.getServer();
            singleplayer.getConnection().waitForChunksRender();

            setUpArena();
            fillHotbar("creative");

            // Enabling must jump from an empty slot to one of the four block slots and highlight
            // exactly those, not the sword or the empty slots.
            server.runCommand("item replace entity @a hotbar.5 with minecraft:diamond_sword");
            context.waitFor(mc -> !mc.player.getInventory().getItem(5).isEmpty());
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(8));
            context.waitTicks(2);
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitTicks(2);
            int slotAfterEnable = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
            LOGGER.info("[PaletteShuffleTest] slot after enabling: {}", slotAfterEnable);
            check(slotAfterEnable >= 0 && slotAfterEnable < HOTBAR_BLOCKS.length,
                    "enabling did not select a block slot, selected " + slotAfterEnable);
            checkHighlight("automatic", Set.of(0, 1, 2, 3), Set.of());

            analyze("creative / spam clicking, 1 click per tick", spamPlacements(200, 1, 0), false);
            analyze("creative / spam clicking, 1 click per 1-3 ticks", spamPlacements(200, 1, 2), false);
            analyze("creative / spam clicking, 2 clicks per tick", spamPlacements(100, 2, 1), false);
            analyze("creative / single clicks", clickPlacements(200), true);
            analyze("creative / holding right-click", holdPlacements(150), false);

            fillHotbar("survival");
            analyze("survival / single clicks", clickPlacements(120), true);

            // Disabling must stop the switching.
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitTicks(2);
            List<Placement> disabled = clickPlacements(10);
            long switches = disabled.stream().filter(p -> p.slotBefore != p.slotAfter).count();
            LOGGER.info("[PaletteShuffleTest] disabled: {} slot switches in {} placements", switches, disabled.size());
            check(switches == 0, "slot still switched while shuffle mode was disabled");

            testManualMode();
        }
    }

    /**
     * Configures manual mode through the settings screen like a player would: stone weight 3,
     * glass weight 1, oak planks unchecked and gold weight 2. Then only the three checked block slots
     * may be highlighted, and they must be placed 3:1:2.
     */
    private void testManualMode() {
        fillHotbar("creative");
        server.runCommand("item replace entity @a hotbar.5 with minecraft:diamond_sword");
        context.waitFor(mc -> !mc.player.getInventory().getItem(5).isEmpty());

        TestInput input = context.getInput();
        input.pressKey(InputConstants.KEY_J);
        context.waitForScreen(ShuffleSettingsScreen.class);
        context.takeScreenshot("settings_automatic");

        clickWidget(CycleButton.class, 0);
        clickWidget(Checkbox.class, 2);
        typeWeight(0, "3");
        typeWeight(3, "2");
        context.takeScreenshot("settings_manual");
        context.clickScreenButton("gui.done");
        context.waitForScreen(null);

        JsonObject saved;
        try {
            saved = JsonParser.parseString(Files.readString(
                    FabricLoader.getInstance().getConfigDir().resolve("paletteshuffle.json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.info("[PaletteShuffleTest] saved settings: {}", saved);
        check(saved.get("manual").getAsBoolean(), "manual mode was not saved");
        check(saved.get("weights").toString().startsWith("[3,1,1,2,"), "weights were not saved: " + saved);
        check(saved.get("selected").toString().startsWith("[true,true,false,true,"), "slots were not saved: " + saved);

        input.pressKey(InputConstants.KEY_K);
        checkHighlight("manual", Set.of(0, 1, 3), Set.of(0, 1, 3));

        analyze("manual / stone 3, glass 1, planks off, gold 2", spamPlacements(400, 1, 0), false,
                new double[] {3 / 6.0, 1 / 6.0, 0, 2 / 6.0});
    }

    /** Clicks the {@code index}-th widget of this type on the open screen, counted from the left. */
    private void clickWidget(Class<? extends AbstractWidget> type, int index) {
        double[] center = context.computeOnClient(mc -> {
            AbstractWidget widget = mc.gui.screen().children().stream()
                    .filter(type::isInstance)
                    .map(AbstractWidget.class::cast)
                    .sorted(Comparator.comparingInt(AbstractWidget::getX))
                    .toList()
                    .get(index);
            int scale = mc.getWindow().getGuiScale();
            return new double[] {
                    (widget.getX() + widget.getWidth() / 2.0) * scale,
                    (widget.getY() + widget.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(center[0], center[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private void typeWeight(int slot, String weight) {
        clickWidget(EditBox.class, slot);
        TestInput input = context.getInput();
        input.pressKey(InputConstants.KEY_END);
        for (int i = 0; i < 3; i++) {
            input.pressKey(InputConstants.KEY_BACKSPACE);
        }
        input.typeChars(weight);
    }

    private void setUpArena() {
        BlockPos spawn = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
        origin = spawn.above(20);
        support = origin.east(2);
        placePos = support.above();

        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:bedrock",
                origin.getX() - 2, origin.getY(), origin.getZ() - 2, origin.getX() + 4, origin.getY(), origin.getZ() + 2));
        server.runCommand(String.format("tp @a %d.5 %d %d.5 -90 0", origin.getX(), origin.getY() + 1, origin.getZ()));
        context.waitFor(mc -> mc.player.blockPosition().equals(origin.above()));
        context.waitTicks(5);
        context.getInput().lookAt(support);
        context.waitTick();
    }

    private void fillHotbar(String gameMode) {
        server.runCommand("gamemode " + gameMode + " @a");
        server.runCommand("clear @a");
        for (int slot = 0; slot < HOTBAR_BLOCKS.length; slot++) {
            server.runCommand("item replace entity @a hotbar." + slot + " with minecraft:" + HOTBAR_BLOCKS[slot] + " 64");
        }
        context.waitFor(mc -> mc.player.getInventory().getItem(HOTBAR_BLOCKS.length - 1).getCount() == 64);
        context.waitTicks(2);
    }

    /** Places blocks one right-click at a time, recording the held block and the resulting slots. */
    private List<Placement> clickPlacements(int count) {
        List<Placement> placements = new ArrayList<>();
        TestInput input = context.getInput();
        for (int i = 0; i < count; i++) {
            int slotBefore = selectedSlot();
            Block held = context.computeOnClient(mc ->
                    mc.player.getMainHandItem().getItem() instanceof BlockItem item ? item.getBlock() : null);

            input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            server.waitFor(s -> !s.overworld().getBlockState(placePos).isAir(), 40);
            Block placed = server.computeOnServer(s -> s.overworld().getBlockState(placePos).getBlock());
            context.waitTicks(2);
            placements.add(new Placement(held, placed, slotBefore, selectedSlot()));

            server.runCommand(String.format("setblock %d %d %d air", placePos.getX(), placePos.getY(), placePos.getZ()));
            context.waitFor(mc -> mc.level.getBlockState(placePos).isAir());
        }
        return placements;
    }

    /**
     * Clicks right-click as fast as a player spamming the button, without waiting for the world to
     * settle in between, so the client's view of the world lags behind the server like in real play.
     * A server tick hook records and removes every block placed in front of the player.
     *
     * @param clicksPerTick clicks queued before each tick; more than one means several clicks are
     *                      processed within the same tick
     * @param maxGapTicks   idle ticks after each burst, chosen randomly between 0 and this value
     */
    private List<Placement> spamPlacements(int bursts, int clicksPerTick, int maxGapTicks) {
        BlockPos min = origin.offset(1, 1, -1);
        BlockPos max = origin.offset(3, 3, 1);
        List<Block> placed = Collections.synchronizedList(new ArrayList<>());
        Random random = new Random(1);

        spamRecorder = level -> {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    placed.add(state.getBlock());
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        };
        TestInput input = context.getInput();
        for (int i = 0; i < bursts; i++) {
            for (int click = 0; click < clicksPerTick; click++) {
                input.holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                input.releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            }
            context.waitTicks(1 + random.nextInt(maxGapTicks + 1));
        }
        context.waitTicks(10);
        spamRecorder = null;

        synchronized (placed) {
            LOGGER.info("[PaletteShuffleTest] spam: {} clicks, {} blocks placed", bursts * clicksPerTick, placed.size());
            return placed.stream().map(block -> new Placement(block, block, 0, 0)).toList();
        }
    }

    /** Holds right-click so vanilla repeats the placement, recording every block that appears. */
    private List<Placement> holdPlacements(int count) {
        List<Placement> placements = new ArrayList<>();
        TestInput input = context.getInput();
        BlockPos min = origin.offset(1, 1, -1);
        BlockPos max = origin.offset(3, 3, 1);
        while (placements.size() < count) {
            Map<BlockPos, Block> known = new LinkedHashMap<>();
            int slot = selectedSlot();
            input.holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            for (int tick = 0; tick < 24 && placements.size() < count; tick++) {
                context.waitTick();
                Map<BlockPos, Block> current = server.computeOnServer(s -> {
                    Map<BlockPos, Block> blocks = new LinkedHashMap<>();
                    for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                        Block block = s.overworld().getBlockState(pos).getBlock();
                        if (block != Blocks.AIR) {
                            blocks.put(pos.immutable(), block);
                        }
                    }
                    return blocks;
                });
                for (Map.Entry<BlockPos, Block> entry : current.entrySet()) {
                    if (known.put(entry.getKey(), entry.getValue()) == null) {
                        int slotAfter = selectedSlot();
                        placements.add(new Placement(null, entry.getValue(), slot, slotAfter));
                        slot = slotAfter;
                    }
                }
            }
            input.releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);
            server.runCommand(String.format("fill %d %d %d %d %d %d air",
                    min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
            context.waitFor(mc -> mc.level.getBlockState(placePos).isAir());
            context.waitTicks(2);
        }
        return placements;
    }

    /**
     * Expects shuffle mode to be enabled and compares the top-left pixel of every hotbar slot's
     * interior, which no item covers, with the same pixel once shuffle mode is turned off again.
     * Turning it off keeps the selected slot, so the first-person hand, which shows through the
     * translucent hotbar, looks the same in both screenshots. Shuffle mode is enabled again afterwards.
     * Also checks that a chance label appears right above exactly the {@code labelledSlots}.
     */
    private void checkHighlight(String name, Set<Integer> highlightedSlots, Set<Integer> labelledSlots) {
        TestInput input = context.getInput();
        context.waitTicks(10);
        Path on = context.takeScreenshot(name + "_highlight_on");
        input.pressKey(InputConstants.KEY_K);
        context.waitTicks(2);
        Path off = context.takeScreenshot(name + "_highlight_off");
        input.pressKey(InputConstants.KEY_K);
        context.waitTicks(2);

        int guiWidth = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int guiHeight = context.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());

        try (NativeImage offImage = NativeImage.read(Files.newInputStream(off));
             NativeImage onImage = NativeImage.read(Files.newInputStream(on))) {
            double scale = (double) onImage.getWidth() / guiWidth;
            StringBuilder gains = new StringBuilder();
            for (int slot = 0; slot < 9; slot++) {
                int x = (int) ((guiWidth / 2 - 91 + 3 + slot * 20 + 0.5) * scale);
                int y = (int) ((guiHeight - 22 + 3 + 0.5) * scale);
                int greenGain = ARGB.green(onImage.getPixel(x, y)) - ARGB.green(offImage.getPixel(x, y));
                gains.append(slot).append('=').append(greenGain).append(' ');

                boolean shouldHighlight = highlightedSlots.contains(slot);
                check(shouldHighlight ? greenGain > 30 : Math.abs(greenGain) < 10, "slot " + slot
                        + (shouldHighlight ? " not highlighted" : " highlighted") + ", green gain " + greenGain);
            }
            LOGGER.info("[PaletteShuffleTest] {} highlight green gain per slot: {}", name, gains);

            // White label text in the 20x9 area above each slot, compared with the screenshot without it.
            StringBuilder labelPixels = new StringBuilder();
            for (int slot = 0; slot < 9; slot++) {
                int newWhitePixels = 0;
                for (int gx = guiWidth / 2 - 91 + 1 + slot * 20; gx < guiWidth / 2 - 91 + 21 + slot * 20; gx++) {
                    for (int gy = guiHeight - 31; gy < guiHeight - 22; gy++) {
                        int x = (int) ((gx + 0.5) * scale);
                        int y = (int) ((gy + 0.5) * scale);
                        if (isWhite(onImage.getPixel(x, y)) && !isWhite(offImage.getPixel(x, y))) {
                            newWhitePixels++;
                        }
                    }
                }
                labelPixels.append(slot).append('=').append(newWhitePixels).append(' ');

                boolean shouldLabel = labelledSlots.contains(slot);
                check(shouldLabel ? newWhitePixels > 15 : newWhitePixels == 0, "slot " + slot
                        + (shouldLabel ? " has no chance label" : " has a chance label") + ", " + newWhitePixels + " white pixels");
            }
            LOGGER.info("[PaletteShuffleTest] {} chance label pixels per slot: {}", name, labelPixels);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isWhite(int argb) {
        return ARGB.red(argb) > 230 && ARGB.green(argb) > 230 && ARGB.blue(argb) > 230;
    }

    private int selectedSlot() {
        return context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
    }

    private void analyze(String name, List<Placement> placements, boolean checkHeld) {
        analyze(name, placements, checkHeld, new double[] {0.25, 0.25, 0.25, 0.25});
    }

    /**
     * Checks the placed blocks against the expected probability of each of the {@link #HOTBAR_BLOCKS},
     * allowing four standard deviations of random noise.
     */
    private void analyze(String name, List<Placement> placements, boolean checkHeld, double[] expected) {
        int n = placements.size();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : HOTBAR_BLOCKS) {
            counts.put(id, 0);
        }
        int mismatches = 0;
        int sameAsPrevious = 0;
        int longestRun = 1;
        int run = 1;
        StringBuilder sequence = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Placement p = placements.get(i);
            String id = BuiltInRegistries.BLOCK.getKey(p.placed).getPath();
            counts.merge(id, 1, Integer::sum);
            sequence.append(List.of(HOTBAR_BLOCKS).indexOf(id));
            if (checkHeld && p.held != p.placed) {
                mismatches++;
            }
            if (i > 0 && placements.get(i - 1).placed == p.placed) {
                sameAsPrevious++;
                longestRun = Math.max(longestRun, ++run);
            } else {
                run = 1;
            }
        }

        double expectedRepeats = 0;
        double maxProbability = 0;
        StringBuilder expectedCounts = new StringBuilder();
        for (int i = 0; i < expected.length; i++) {
            expectedRepeats += expected[i] * expected[i];
            maxProbability = Math.max(maxProbability, expected[i]);
            expectedCounts.append(HOTBAR_BLOCKS[i]).append('=').append(Math.round(n * expected[i])).append(' ');
        }
        // A longer run than this happens by chance in fewer than 1 in 1000 test runs.
        int maxRun = 1;
        while (n * Math.pow(maxProbability, maxRun) >= 0.001) {
            maxRun++;
        }

        LOGGER.info("[PaletteShuffleTest] === {} ({} placements) ===", name, n);
        LOGGER.info("[PaletteShuffleTest] counts: {} (expected {})", counts, expectedCounts.toString().trim());
        LOGGER.info("[PaletteShuffleTest] same block as previous: {} ({}%, expected ~{}%)", sameAsPrevious,
                Math.round(100.0 * sameAsPrevious / (n - 1)), Math.round(100 * expectedRepeats));
        LOGGER.info("[PaletteShuffleTest] longest run of one block: {}", longestRun);
        LOGGER.info("[PaletteShuffleTest] placed != held: {}", mismatches);
        LOGGER.info("[PaletteShuffleTest] sequence (hotbar slot of each placed block): {}", sequence);

        check(counts.size() == HOTBAR_BLOCKS.length, name + ": unexpected block placed " + counts);
        for (int i = 0; i < HOTBAR_BLOCKS.length; i++) {
            int count = counts.get(HOTBAR_BLOCKS[i]);
            double mean = n * expected[i];
            double tolerance = 4 * Math.sqrt(n * expected[i] * (1 - expected[i])) + 1;
            check(Math.abs(count - mean) <= tolerance, name + ": " + HOTBAR_BLOCKS[i] + " placed " + count
                    + " of " + n + " times, expected " + Math.round(mean));
        }
        check(sameAsPrevious < (n - 1) * (expectedRepeats + 0.15), name + ": too many repeats " + sameAsPrevious);
        check(longestRun <= maxRun, name + ": run of " + longestRun + " identical blocks");
        check(mismatches == 0, name + ": " + mismatches + " placements used a different block than held");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Placement(Block held, Block placed, int slotBefore, int slotAfter) {
    }
}
