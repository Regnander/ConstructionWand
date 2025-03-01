package thetadev.constructionwand.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandAction;
import thetadev.constructionwand.api.IWandSupplier;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.ModStats;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.ModItems;
import thetadev.constructionwand.items.wand.ItemWand;
import thetadev.constructionwand.wand.supplier.SupplierInventory;
import thetadev.constructionwand.wand.supplier.SupplierRandom;
import thetadev.constructionwand.wand.undo.ISnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WandJob
{
    public final Player player;
    public final Level world;
    public final BlockHitResult rayTraceResult;
    public final WandOptions options;
    public final ItemStack wand;
    public final ItemWand wandItem;

    private final IWandAction wandAction;
    private final IWandSupplier wandSupplier;

    private List<ISnapshot> placeSnapshots;

    public WandJob(Player player, Level world, BlockHitResult rayTraceResult, ItemStack wand) {
        this.player = player;
        this.world = world;
        this.rayTraceResult = rayTraceResult;
        this.placeSnapshots = new ArrayList<>();

        // Get wand
        this.wand = wand;
        this.wandItem = (ItemWand) wand.getItem();
        options = new WandOptions(wand);

        // Select wand action and supplier based on options
        wandSupplier = options.random.get() ?
                new SupplierRandom(player, options) : new SupplierInventory(player, options);
        wandAction = options.cores.get().getWandAction();

        wandSupplier.getSupply(getTargetItem(world, rayTraceResult));
    }

    @Nullable
    private static BlockItem getTargetItem(Level world, BlockHitResult rayTraceResult) {
        // Get target item
        Item tgitem = world.getBlockState(rayTraceResult.getBlockPos()).getBlock().asItem();
        if(!(tgitem instanceof BlockItem)) return null;
        return (BlockItem) tgitem;
    }

    public void getSnapshots() {
        int limit;
        // Infinity wand gets enhanced limit in creative mode
        if(player.isCreative() && wandItem == ModItems.WAND_INFINITY) limit = ConfigServer.LIMIT_CREATIVE.get();
        else limit = Math.min(wandItem.remainingDurability(wand), wandAction.getLimit(wand));

        if(rayTraceResult.getType() == HitResult.Type.BLOCK)
            placeSnapshots = wandAction.getSnapshots(world, player, rayTraceResult, wand, options, wandSupplier, limit);
        else
            placeSnapshots = wandAction.getSnapshotsFromAir(world, player, rayTraceResult, wand, options, wandSupplier, limit);
    }

    public Set<BlockPos> getBlockPositions() {
        return placeSnapshots.stream().map(ISnapshot::getPos).collect(Collectors.toSet());
    }

    public boolean doIt() {
        ArrayList<ISnapshot> executed = new ArrayList<>();

        for(ISnapshot snapshot : placeSnapshots) {
            if(wand.isEmpty() || wandItem.remainingDurability(wand) == 0) break;

            if(snapshot.execute(world, player, rayTraceResult)) {
                // If the item cant be taken, undo the placement
                if(wandSupplier.takeItemStack(snapshot.getRequiredItems()) == 0) executed.add(snapshot);
                else {
                    ConstructionWand.LOGGER.info("Item could not be taken. Remove block: " +
                            snapshot.getBlockState().getBlock().toString());
                    snapshot.forceRestore(world);
                }

                wand.hurt(1, player.getRandom(), (ServerPlayer) player);
                player.awardStat(ModStats.USE_WAND);
            }
        }
        placeSnapshots = executed;

        // Play place sound
        if(!placeSnapshots.isEmpty()) {
            SoundType sound = placeSnapshots.get(0).getBlockState().getSoundType();
            world.playSound(null, WandUtil.playerPos(player), sound.getPlaceSound(), SoundSource.BLOCKS, sound.volume, sound.pitch);

            // Add to job history for undo
            ConstructionWand.instance.undoHistory.add(player, world, placeSnapshots);
        }

        return !placeSnapshots.isEmpty();
    }
}