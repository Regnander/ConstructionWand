package thetadev.constructionwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.option.IOption;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.network.PacketWandOption;

import javax.annotation.Nonnull;

public class ScreenWand extends Screen
{
    private final ItemStack wand;
    private final WandOptions wandOptions;

    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING_WIDTH = 50;
    private static final int SPACING_HEIGHT = 30;
    private static final int N_COLS = 2;
    private static final int N_ROWS = 3;

    private static final int FIELD_WIDTH = N_COLS * (BUTTON_WIDTH + SPACING_WIDTH) - SPACING_WIDTH;
    private static final int FIELD_HEIGHT = N_ROWS * (BUTTON_HEIGHT + SPACING_HEIGHT) - SPACING_HEIGHT;

    public ScreenWand(ItemStack wand) {
        super(new TextComponent("ScreenWand"));
        this.wand = wand;
        wandOptions = new WandOptions(wand);
    }

    @Override
    protected void init() {
        createButton(0, 0, wandOptions.cores);
        createButton(0, 1, wandOptions.lock);
        createButton(0, 2, wandOptions.direction);
        createButton(1, 0, wandOptions.replace);
        createButton(1, 1, wandOptions.match);
        createButton(1, 2, wandOptions.random);
    }

    @Override
    public void render(@Nonnull PoseStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrixStack);
        drawCenteredString(matrixStack, font, wand.getDisplayName(), width / 2, height / 2 - FIELD_HEIGHT / 2 - SPACING_HEIGHT, 16777215);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean charTyped(char character, int code) {
        if(character == 'e') onClose();
        return super.charTyped(character, code);
    }

    private void createButton(int cx, int cy, IOption<?> option) {
        Button button = new Button(getX(cx), getY(cy), BUTTON_WIDTH, BUTTON_HEIGHT, getButtonLabel(option), bt -> clickButton(bt, option), (bt, ms, x, y) -> drawTooltip(ms, x, y, option));
        button.active = option.isEnabled();
        addRenderableWidget(button);
    }

    private void clickButton(Button button, IOption<?> option) {
        option.next();
        ConstructionWand.instance.HANDLER.sendToServer(new PacketWandOption(option, false));
        button.setMessage(getButtonLabel(option));
    }

    private void drawTooltip(PoseStack matrixStack, int mouseX, int mouseY, IOption<?> option) {
        if(isMouseOver(mouseX, mouseY)) {
            renderTooltip(matrixStack, new TranslatableComponent(option.getDescTranslation()), mouseX, mouseY);
        }
    }

    private int getX(int n) {
        return width / 2 - FIELD_WIDTH / 2 + n * (BUTTON_WIDTH + SPACING_WIDTH);
    }

    private int getY(int n) {
        return height / 2 - FIELD_HEIGHT / 2 + n * (BUTTON_HEIGHT + SPACING_HEIGHT);
    }

    private Component getButtonLabel(IOption<?> option) {
        return new TranslatableComponent(option.getKeyTranslation()).append(new TranslatableComponent(option.getValueTranslation()));
    }
}
