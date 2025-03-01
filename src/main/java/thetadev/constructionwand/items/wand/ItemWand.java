package thetadev.constructionwand.items.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.generators.ModelFile;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.IOption;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.data.ICustomItemModel;
import thetadev.constructionwand.data.ItemModelGenerator;
import thetadev.constructionwand.items.ItemBase;
import thetadev.constructionwand.wand.WandJob;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class ItemWand extends ItemBase implements ICustomItemModel
{
    public ItemWand(String name, Properties properties) {
        super(name, properties);
    }

    @Nonnull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        Level world = context.getLevel();

        if(world.isClientSide || player == null) return InteractionResult.FAIL;

        ItemStack stack = player.getItemInHand(hand);

        if(player.isCrouching() && ConstructionWand.instance.undoHistory.isUndoActive(player)) {
            return ConstructionWand.instance.undoHistory.undo(player, world, context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        else {
            WandJob job = getWandJob(player, world, new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false), stack);
            return job.doIt() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level world, Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if(!player.isCrouching()) {
            if(world.isClientSide) return InteractionResultHolder.fail(stack);

            // Right click: Place angel block
            WandJob job = getWandJob(player, world, BlockHitResult.miss(player.getLookAngle(),
                    WandUtil.fromVector(player.getLookAngle()), WandUtil.playerPos(player)), stack);
            return job.doIt() ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    public static WandJob getWandJob(Player player, Level world, @Nullable BlockHitResult rayTraceResult, ItemStack wand) {
        WandJob wandJob = new WandJob(player, world, rayTraceResult, wand);
        wandJob.getSnapshots();

        return wandJob;
    }

    @Override
    public boolean isCorrectToolForDrops(@Nonnull BlockState blockIn) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(@Nonnull ItemStack toRepair, @Nonnull ItemStack repair) {
        return false;
    }

    public int remainingDurability(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@Nonnull ItemStack itemstack, Level worldIn, @Nonnull List<Component> lines, @Nonnull TooltipFlag extraInfo) {
        WandOptions options = new WandOptions(itemstack);
        int limit = options.cores.get().getWandAction().getLimit(itemstack);

        String langTooltip = ConstructionWand.MODID + ".tooltip.";

        // +SHIFT tooltip: show all options + installed cores
        if(Screen.hasShiftDown()) {
            for(int i = 1; i < options.allOptions.length; i++) {
                IOption<?> opt = options.allOptions[i];
                lines.add(new TranslatableComponent(opt.getKeyTranslation()).withStyle(ChatFormatting.AQUA)
                        .append(new TranslatableComponent(opt.getValueTranslation()).withStyle(ChatFormatting.GRAY))
                );
            }
            if(!options.cores.getUpgrades().isEmpty()) {
                lines.add(new TextComponent(""));
                lines.add(new TranslatableComponent(langTooltip + "cores").withStyle(ChatFormatting.GRAY));

                for(IWandCore core : options.cores.getUpgrades()) {
                    lines.add(new TranslatableComponent(options.cores.getKeyTranslation() + "." + core.getRegistryName().toString()));
                }
            }
        }
        // Default tooltip: show block limit + active wand core
        else {
            IOption<?> opt = options.allOptions[0];
            lines.add(new TranslatableComponent(langTooltip + "blocks", limit).withStyle(ChatFormatting.GRAY));
            lines.add(new TranslatableComponent(opt.getKeyTranslation()).withStyle(ChatFormatting.AQUA)
                    .append(new TranslatableComponent(opt.getValueTranslation()).withStyle(ChatFormatting.WHITE)));
            lines.add(new TranslatableComponent(langTooltip + "shift").withStyle(ChatFormatting.AQUA));
        }
    }

    public static void optionMessage(Player player, IOption<?> option) {
        player.displayClientMessage(
                new TranslatableComponent(option.getKeyTranslation()).withStyle(ChatFormatting.AQUA)
                        .append(new TranslatableComponent(option.getValueTranslation()).withStyle(ChatFormatting.WHITE))
                        .append(new TextComponent(" - ").withStyle(ChatFormatting.GRAY))
                        .append(new TranslatableComponent(option.getDescTranslation()).withStyle(ChatFormatting.WHITE))
                , true);
    }

    @Override
    public void generateCustomItemModel(ItemModelGenerator generator, String name) {
        ModelFile wandWithCore = generator.withExistingParent(name + "_core", "item/handheld")
                .texture("layer0", generator.modLoc("item/" + name))
                .texture("layer1", generator.modLoc("item/overlay_core"));

        generator.withExistingParent(name, "item/handheld")
                .texture("layer0", generator.modLoc("item/" + name))
                .override()
                .predicate(generator.modLoc("using_core"), 1)
                .model(wandWithCore).end();

    }
}
