package org.jarnerolf.blockshuffle.client;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Lets the player choose between automatic mode (every block slot equally likely) and manual mode,
 * where each hotbar slot can be included and given an integer weight. One column per hotbar slot.
 */
public class ShuffleSettingsScreen extends Screen {

    private static final int LABEL_WIDTH = 50;
    private static final int COLUMN_WIDTH = 36;
    private static final int MODE_Y = 32;
    private static final int ICON_Y = 66;
    private static final int USE_Y = 88;
    private static final int WEIGHT_Y = 110;
    private static final int CHANCE_Y = 134;
    private static final int HINT_OFFSET = 18;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFA0A0A0;
    private static final int HIGHLIGHT_COLOR = 0x6655FF55;
    private static final DecimalFormat PERCENT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final Screen parent;
    private final ShuffleConfig config = ShuffleConfig.get();
    private final List<Checkbox> useBoxes = new ArrayList<>();
    private final List<EditBox> weightBoxes = new ArrayList<>();
    private int columnsLeft;

    /** @param parent the screen to return to, e.g. Mod Menu's, or {@code null} to return to the game */
    public ShuffleSettingsScreen(Screen parent) {
        super(Component.translatable("screen.blockshuffle.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        useBoxes.clear();
        weightBoxes.clear();
        columnsLeft = (width - LABEL_WIDTH - Inventory.getSelectionSize() * COLUMN_WIDTH) / 2 + LABEL_WIDTH;

        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("screen.blockshuffle.settings.mode.manual"),
                        Component.translatable("screen.blockshuffle.settings.mode.automatic"),
                        config.isManual())
                .withTooltip(manual -> Tooltip.create(Component.translatable(manual
                        ? "screen.blockshuffle.settings.mode.manual.tooltip"
                        : "screen.blockshuffle.settings.mode.automatic.tooltip")))
                .create(width / 2 - 100, MODE_Y, 200, 20, Component.translatable("screen.blockshuffle.settings.mode"),
                        (button, manual) -> {
                            config.setManual(manual);
                            updateWidgetStates();
                        }));

        int boxSize = Checkbox.getBoxSize(font);
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            int slotIndex = slot;
            int centerX = columnCenter(slot);

            useBoxes.add(addRenderableWidget(Checkbox.builder(Component.empty(), font)
                    .pos(centerX - boxSize / 2, USE_Y)
                    .selected(config.isSelected(slot))
                    .onValueChange((checkbox, selected) -> {
                        config.setSelected(slotIndex, selected);
                        updateWidgetStates();
                    })
                    .build()));

            EditBox weightBox = new EditBox(font, centerX - 15, WEIGHT_Y, 30, 16,
                    Component.translatable("screen.blockshuffle.settings.weight.narration", slot + 1));
            weightBox.setMaxLength(String.valueOf(ShuffleConfig.MAX_WEIGHT).length());
            weightBox.setCentered(true);
            weightBox.setValue(String.valueOf(config.getWeight(slot)));
            weightBox.setResponder(value -> onWeightChanged(slotIndex, weightBox, value));
            weightBoxes.add(addRenderableWidget(weightBox));
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20)
                .build());
        updateWidgetStates();
    }

    private void onWeightChanged(int slot, EditBox weightBox, String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (!digits.equals(value)) {
            // Re-enters this responder with the cleaned value.
            weightBox.setValue(digits);
            return;
        }
        config.setWeight(slot, digits.isEmpty() ? 0 : Integer.parseInt(digits));
    }

    /** The per-slot controls only exist in manual mode, and a weight only matters for an included slot. */
    private void updateWidgetStates() {
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            boolean weightEditable = config.isManual() && config.isSelected(slot);
            useBoxes.get(slot).visible = config.isManual();
            weightBoxes.get(slot).visible = config.isManual();
            weightBoxes.get(slot).active = weightEditable;
            weightBoxes.get(slot).setEditable(weightEditable);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (BlockshuffleClient.settingsKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        config.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 15, TEXT_COLOR);

        // Without the manual-only rows, the chances move up right below the slots.
        int chanceY = config.isManual() ? CHANCE_Y : USE_Y + 4;
        int labelX = columnsLeft - LABEL_WIDTH;
        graphics.text(font, Component.translatable("screen.blockshuffle.settings.slot"), labelX, ICON_Y + 4, TEXT_COLOR);
        if (config.isManual()) {
            graphics.text(font, Component.translatable("screen.blockshuffle.settings.use"), labelX, USE_Y + 4, TEXT_COLOR);
            graphics.text(font, Component.translatable("screen.blockshuffle.settings.weight"), labelX, WEIGHT_Y + 4, TEXT_COLOR);
        }

        if (minecraft.player == null) {
            // Opened from Mod Menu outside a world: there is no hotbar to show the blocks and chances of.
            for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
                graphics.centeredText(font, String.valueOf(slot + 1), columnCenter(slot), ICON_Y + 4, TEXT_COLOR);
            }
            graphics.centeredText(font, Component.translatable("screen.blockshuffle.settings.chance.no_world"),
                    width / 2, chanceY, MUTED_TEXT_COLOR);
        } else {
            graphics.text(font, Component.translatable("screen.blockshuffle.settings.chance"), labelX, chanceY, TEXT_COLOR);

            // Chances follow the current hotbar: empty slots and non-blocks are never picked.
            Inventory inventory = minecraft.player.getInventory();
            int[] weights = ShuffleMode.slotWeights(inventory);
            int totalWeight = Arrays.stream(weights).sum();

            for (int slot = 0; slot < weights.length; slot++) {
                int centerX = columnCenter(slot);
                ItemStack stack = inventory.getItem(slot);
                if (weights[slot] > 0) {
                    graphics.fill(centerX - 9, ICON_Y - 1, centerX + 9, ICON_Y + 17, HIGHLIGHT_COLOR);
                }
                graphics.item(stack, centerX - 8, ICON_Y);
                graphics.itemDecorations(font, stack, centerX - 8, ICON_Y);

                String chance = weights[slot] > 0 ? PERCENT.format(100.0 * weights[slot] / totalWeight) + "%" : "-";
                graphics.centeredText(font, chance, centerX, chanceY, weights[slot] > 0 ? TEXT_COLOR : MUTED_TEXT_COLOR);
            }
        }

        graphics.centeredText(font, Component.translatable(config.isManual()
                        ? "screen.blockshuffle.settings.hint.manual"
                        : "screen.blockshuffle.settings.hint.automatic"),
                width / 2, chanceY + HINT_OFFSET, MUTED_TEXT_COLOR);
    }

    private int columnCenter(int slot) {
        return columnsLeft + slot * COLUMN_WIDTH + COLUMN_WIDTH / 2;
    }
}
