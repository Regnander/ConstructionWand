package thetadev.constructionwand.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.ConfigClient;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.network.PacketQueryUndo;
import thetadev.constructionwand.network.PacketWandOption;

public class ClientEvents
{
    private boolean optPressed;

    public ClientEvents() {
        optPressed = false;
    }

    // Send state of OPT key to server
    @SubscribeEvent
    public void KeyEvent(InputEvent.KeyInputEvent event) {
        Player player = Minecraft.getInstance().player;
        if(player == null) return;
        if(WandUtil.holdingWand(player) == null) return;

        boolean optState = isOptKeyDown();
        if(optPressed != optState) {
            optPressed = optState;
            PacketQueryUndo packet = new PacketQueryUndo(optPressed);
            ConstructionWand.instance.HANDLER.sendToServer(packet);
            //ConstructionWand.LOGGER.debug("OPT key update: " + optPressed);
        }
    }

    // Sneak+(OPT)+Scroll to change direction lock
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void MouseScrollEvent(InputEvent.MouseScrollEvent event) {
        Player player = Minecraft.getInstance().player;
        double scroll = event.getScrollDelta();

        if(player == null || !modeKeyCombDown(player) || scroll == 0) return;

        ItemStack wand = WandUtil.holdingWand(player);
        if(wand == null) return;

        WandOptions wandOptions = new WandOptions(wand);
        wandOptions.lock.next(scroll < 0);
        ConstructionWand.instance.HANDLER.sendToServer(new PacketWandOption(wandOptions.lock, true));
        event.setCanceled(true);
    }

    // Sneak+(OPT)+Left click wand to change core
    @SubscribeEvent
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getPlayer();

        if(player == null || !modeKeyCombDown(player)) return;

        ItemStack wand = event.getItemStack();
        if(!(wand.getItem() instanceof ItemWand)) return;

        WandOptions wandOptions = new WandOptions(wand);
        wandOptions.cores.next();
        ConstructionWand.instance.HANDLER.sendToServer(new PacketWandOption(wandOptions.cores, true));
    }

    // Sneak+(OPT)+Right click wand to open GUI
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if(event.getSide().isServer()) return;

        Player player = event.getPlayer();
        if(player == null || !guiKeyCombDown(player)) return;

        ItemStack wand = event.getItemStack();
        if(!(wand.getItem() instanceof ItemWand)) return;

        Minecraft.getInstance().setScreen(new ScreenWand(wand));
        event.setCanceled(true);
    }

    private static boolean isKeyDown(int id) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), id);
    }

    public static boolean isOptKeyDown() {
        return isKeyDown(ConfigClient.OPT_KEY.get());
    }

    public static boolean modeKeyCombDown(Player player) {
        return player.isCrouching() && (isOptKeyDown() || !ConfigClient.SHIFTOPT_MODE.get());
    }

    public static boolean guiKeyCombDown(Player player) {
        return player.isCrouching() && (isOptKeyDown() || !ConfigClient.SHIFTOPT_GUI.get());
    }
}
