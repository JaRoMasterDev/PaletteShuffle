package org.jarnerolf.blockshuffle.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jarnerolf.blockshuffle.client.ShuffleSettingsScreen;

/**
 * Opens the settings through Mod Menu like a player would, from the title screen and from the pause
 * menu, and checks that closing them returns to Mod Menu.
 */
public class ModMenuClientGameTest implements FabricClientGameTest {

    private static final String MODS_SCREEN = "com.terraformersmc.modmenu.gui.ModsScreen";

    private ClientGameTestContext context;

    @Override
    public void runTest(ClientGameTestContext context) {
        this.context = context;
        TestInput input = context.getInput();

        // Without a world there is no player, so the screen can't show the hotbar.
        context.waitForScreen(TitleScreen.class);
        openSettingsFromModMenu();
        context.takeScreenshot("modmenu_title_automatic");
        clickWidget(screen -> widget(screen, CycleButton.class::isInstance));
        context.takeScreenshot("modmenu_title_manual");
        // Back to automatic mode, which the shuffle test starts from.
        clickWidget(screen -> widget(screen, CycleButton.class::isInstance));
        context.clickScreenButton("gui.done");
        waitForModsScreen();
        context.takeScreenshot("modmenu_list");

        // The settings key closes the screen like Done does.
        clickWidget(ModMenuClientGameTest::configureButton);
        context.waitForScreen(ShuffleSettingsScreen.class);
        input.pressKey(InputConstants.KEY_J);
        waitForModsScreen();
        input.pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(TitleScreen.class);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            TestServerContext server = singleplayer.getServer();
            singleplayer.getConnection().waitForChunksRender();
            server.runCommand("item replace entity @a hotbar.0 with minecraft:granite");
            server.runCommand("item replace entity @a hotbar.1 with minecraft:bricks");
            server.runCommand("item replace entity @a hotbar.2 with minecraft:polished_granite");
            context.waitFor(mc -> !mc.player.getInventory().getItem(2).isEmpty());

            input.pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(PauseScreen.class);
            openSettingsFromModMenu();
            context.takeScreenshot("modmenu_world");
            context.clickScreenButton("gui.done");
            waitForModsScreen();
            input.pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(PauseScreen.class);
        }
    }

    /** Opens Mod Menu from the title or pause screen, selects BlockShuffle and clicks Configure. */
    private void openSettingsFromModMenu() {
        clickWidget(screen -> widget(screen, widget -> widget.getMessage().getString().startsWith("Mods")));
        waitForModsScreen();
        context.getInput().typeChars("BlockShuffle");
        context.waitTick();
        // The first match is BlockShuffle itself, ahead of "BlockShuffle Game Tests".
        clickAt(screen -> {
            AbstractWidget modList = widget(screen, widget -> widget.getClass().getSimpleName().equals("ModListWidget"));
            return new double[] {modList.getX() + modList.getWidth() / 2.0, modList.getY() + 20};
        });
        context.waitTick();

        boolean configurable = context.computeOnClient(mc -> configureButton(mc.gui.screen()).active);
        if (!configurable) {
            throw new AssertionError("Mod Menu has no Configure button for BlockShuffle");
        }
        clickWidget(ModMenuClientGameTest::configureButton);
        context.waitForScreen(ShuffleSettingsScreen.class);
    }

    private void waitForModsScreen() {
        context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getName().equals(MODS_SCREEN));
    }

    private static AbstractWidget configureButton(Screen modsScreen) {
        try {
            Field field = modsScreen.getClass().getDeclaredField("configureButton");
            field.setAccessible(true);
            return (AbstractWidget) field.get(modsScreen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Mod Menu's screen has no configure button", e);
        }
    }

    private static AbstractWidget widget(Screen screen, Predicate<AbstractWidget> filter) {
        return screen.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(filter)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No matching widget on " + screen.getClass().getSimpleName()));
    }

    private void clickWidget(Function<Screen, AbstractWidget> finder) {
        clickAt(screen -> {
            AbstractWidget widget = finder.apply(screen);
            return new double[] {widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0};
        });
    }

    /** Left-clicks a point of the open screen, given in GUI coordinates. */
    private void clickAt(Function<Screen, double[]> point) {
        double[] cursor = context.computeOnClient(mc -> {
            double[] guiPoint = point.apply(mc.gui.screen());
            int scale = mc.getWindow().getGuiScale();
            return new double[] {guiPoint[0] * scale, guiPoint[1] * scale};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
}
