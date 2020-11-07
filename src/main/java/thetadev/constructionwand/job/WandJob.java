package thetadev.constructionwand.job;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.stat.Stats;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.ModStats;
import thetadev.constructionwand.basics.ReplacementRegistry;
import thetadev.constructionwand.basics.WandUtil;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.basics.pool.IPool;
import thetadev.constructionwand.basics.pool.OrderedPool;
import thetadev.constructionwand.basics.pool.RandomPool;
import thetadev.constructionwand.containers.ContainerManager;
import thetadev.constructionwand.items.ItemWand;

import java.util.*;
import java.util.stream.Collectors;

public abstract class WandJob
{
	protected final PlayerEntity player;
	protected final World world;
	protected final BlockHitResult hitResult;
	protected ItemStack wand;
	protected ItemWand wandItem;

	// Wand options
	protected WandOptions options;
	protected int maxBlocks;

	protected HashMap<BlockItem, Integer> itemCounts;
	protected IPool<BlockItem> itemPool;

	protected LinkedList<PlaceSnapshot> placeSnapshots;


	protected WandJob(PlayerEntity player, World world, BlockHitResult hitResult, ItemStack wand)
	{
		this.player = player;
		this.world = world;
		this.hitResult = hitResult;
		placeSnapshots = new LinkedList<>();

		// Get wand
		if(wand == null || wand == ItemStack.EMPTY || !(wand.getItem() instanceof ItemWand)) return;
		this.wand = wand;

		wandItem = (ItemWand) wand.getItem();

		// Get options
		options = new WandOptions(wand);

		// Get place item
		addBlockItems();
		if(itemCounts.isEmpty()) return;

		// Get inventory supply
		for(int v : itemCounts.values()) {
			try {
				maxBlocks = Math.addExact(maxBlocks, v);
			}
			catch(ArithmeticException e) {
				maxBlocks = Integer.MAX_VALUE;
				break;
			}
		}

		maxBlocks = Math.min(maxBlocks, wandItem.getLimit(player, wand));
		if(maxBlocks == 0) return;

		getBlockPositionList();
	}

	public static WandJob getJob(PlayerEntity player, World world, BlockHitResult hitResult, ItemStack itemStack) {
		WandOptions options = new WandOptions(itemStack);

		if(options.mode.get() == WandOptions.MODE.ANGEL) return new TransductionJob(player, world, hitResult, itemStack);
		return new ConstructionJob(player, world, hitResult, itemStack);
	}

	public Set<BlockPos> getBlockPositions() {
		return placeSnapshots.stream().map(snapshot -> snapshot.pos).collect(Collectors.toSet());
	}

	public BlockHitResult getHitResult() { return hitResult; }

	public ItemStack getWand() { return wand; }

	private void addBlockItem(BlockItem item) {
		int count = countItem(item);
		if(count > 0) {
			itemCounts.put(item, count);
			itemPool.add(item);
		}
	}

	private void addBlockItems() {
		itemCounts = new LinkedHashMap<>();

		BlockPos targetPos = hitResult.getBlockPos();
		BlockState targetState = world.getBlockState(targetPos);
		Block targetBlock = targetState.getBlock();
		ItemStack offhandStack = player.getStackInHand(Hand.OFF_HAND);

		// Random mode -> add all items from hotbar
		if(options.random.get()) {
			itemPool = new RandomPool<>(player.getRandom());

			for(ItemStack stack : WandUtil.getHotbarWithOffhand(player)) {
				if(stack.getItem() instanceof BlockItem) addBlockItem((BlockItem) stack.getItem());
			}
		}
		else {
			itemPool = new OrderedPool<>();

			// Block in offhand -> override
			if(!offhandStack.isEmpty() && offhandStack.getItem() instanceof BlockItem) {
				addBlockItem((BlockItem) offhandStack.getItem());
			}
			// Otherwise use target block
			else {
				Item item = targetBlock.asItem();
				if(item instanceof BlockItem) {
					addBlockItem((BlockItem) item);

					// Add replacement items
					if(options.match.get() != WandOptions.MATCH.EXACT) {
						for(Item it : ReplacementRegistry.getMatchingSet(item)) {
							if(it instanceof BlockItem) addBlockItem((BlockItem) it);
						}
					}
				}
			}
		}
	}

	private int countItem(Item item) {
		if(player.inventory == null || player.inventory.main == null) return 0;
		if(player.isCreative()) return Integer.MAX_VALUE;

		int total = 0;
		ContainerManager containerManager = ConstructionWand.instance.containerManager;
		List<ItemStack> inventory = WandUtil.getFullInv(player);

		for(ItemStack stack : inventory) {
			if(stack == null) continue;

			if(WandUtil.stackEquals(stack, item)) {
				total += stack.getCount();
			}
			else {
				int amount = containerManager.countItems(player, new ItemStack(item), stack);
				if(amount == Integer.MAX_VALUE) return Integer.MAX_VALUE;
				total += amount;
			}
		}
		return total;
	}

	// Attempts to take specified number of items, returns number of missing items
	private int takeItems(Item item, int count)
	{
		if(player.inventory == null || player.inventory.main == null) return count;
		if(player.isCreative()) return 0;

		List<ItemStack> hotbar = WandUtil.getHotbarWithOffhand(player);
		List<ItemStack> mainInv = WandUtil.getMainInv(player);

		// Take items from main inv, loose items first
		count = takeItemsInvList(count, item, mainInv, false);
		count = takeItemsInvList(count, item, mainInv, true);

		// Take items from hotbar, containers first
		count = takeItemsInvList(count, item, hotbar, true);
		count = takeItemsInvList(count, item, hotbar, false);

		return count;
	}

	private int takeItemsInvList(int count, Item item, List<ItemStack> inv, boolean container) {
		ContainerManager containerManager = ConstructionWand.instance.containerManager;

		for(ItemStack stack : inv) {
			if(count == 0) break;

			if(container) {
				int nCount = containerManager.useItems(player, new ItemStack(item), stack, count);
				count = nCount;
			}

			if(!container && WandUtil.stackEquals(stack, item)) {
				int toTake = Math.min(count, stack.getCount());
				stack.decrement(toTake);
				count -= toTake;
				player.inventory.markDirty();
			}
		}
		return count;
	}

	protected abstract void getBlockPositionList();

	// Get PlaceSnapshot, or null if no block can be placed
	protected PlaceSnapshot getPlaceSnapshot(BlockPos pos, BlockState supportingBlock) {
		// Is position out of world?
		if(!World.method_24794(pos)) return null;

		// Is block modifiable?
		if(!world.canPlayerModifyAt(player, pos)) return null;

		// If replace mode is off, target has to be air
		if(!options.replace.get() && !world.isAir(pos)) return null;

		// Limit placement range
		if(ConstructionWand.instance.config.MAX_RANGE > 0 && WandUtil.maxRange(hitResult.getBlockPos(), pos) > ConstructionWand.instance.config.MAX_RANGE) return null;

		itemPool.reset();

		while(true) {
			// Draw item from pool (returns null if none are left)
			BlockItem item = itemPool.draw();
			if(item == null) return null;

			int count = itemCounts.get(item);
			if(count == 0) continue;

			// Is block at pos replaceable?
			ItemPlacementContext ctx = new WandItemUseContext(this, pos, item);
			if(!ctx.canPlace()) continue;

			// Can block be placed?
			BlockState blockState = item.getBlock().getPlacementState(ctx);
			if(blockState == null) continue;
			blockState = Block.postProcessState(blockState, world, pos);
			if(blockState.getBlock() == Blocks.AIR || !blockState.canPlaceAt(world, pos)) continue;

			// No entities colliding?
			VoxelShape shape = blockState.getCollisionShape(world, pos);
			if(!shape.isEmpty()) {
				Box blockBB = shape.getBoundingBox().offset(pos);
				if(!world.getEntitiesByClass(LivingEntity.class, blockBB, EntityPredicates.EXCEPT_SPECTATOR).isEmpty()) continue;
			}

			// Reduce item count
			if(count < Integer.MAX_VALUE) itemCounts.merge(item, -1, Integer::sum);
			return new PlaceSnapshot(pos, supportingBlock, item);
		}
	}

	private boolean placeBlock(PlaceSnapshot placeSnapshot) {
		BlockPos blockPos = placeSnapshot.pos;

		ItemPlacementContext ctx = new WandItemUseContext(this, blockPos, placeSnapshot.item);
		if(!ctx.canPlace()) return false;

		BlockState placeBlock = Block.getBlockFromItem(placeSnapshot.item).getPlacementState(ctx);
		if(placeBlock == null) return false;

		BlockState supportingBlock = placeSnapshot.supportingBlock;

		if(options.direction.get() == WandOptions.DIRECTION.TARGET) {
			// Block properties to be copied (alignment/rotation properties)
			for(Property property : new Property[] {
					Properties.HORIZONTAL_FACING, Properties.FACING, Properties.HOPPER_FACING,
					Properties.ROTATION, Properties.AXIS, Properties.BLOCK_HALF, Properties.STAIR_SHAPE})
			{
				if(supportingBlock.contains(property) && placeBlock.contains(property)) {
					placeBlock = placeBlock.with(property, supportingBlock.get(property));
				}
			}

			// Dont dupe double slabs
			if(supportingBlock.contains(Properties.SLAB_TYPE) && placeBlock.contains(Properties.SLAB_TYPE)) {
				SlabType slabType = supportingBlock.get(Properties.SLAB_TYPE);
				if(slabType != SlabType.DOUBLE) placeBlock = placeBlock.with(Properties.SLAB_TYPE, slabType);
			}
		}
		// Place the block
		if(!world.setBlockState(blockPos, placeBlock)) {
			ConstructionWand.LOGGER.info("Block could not be placed");
			return false;
		}

		// Update stats
		player.incrementStat(Stats.USED.getOrCreateStat(placeSnapshot.item));
		player.incrementStat(ModStats.USE_WAND);

		placeSnapshot.block = placeBlock;
		return true;
	}

	protected boolean matchBlocks(Block b1, Block b2) {
		switch(options.match.get()) {
			case EXACT: return b1 == b2;
			case SIMILAR: return ReplacementRegistry.matchBlocks(b1, b2);
			case ANY: return b1 != Blocks.AIR && b2 != Blocks.AIR;
		}
		return false;
	}

	public boolean doIt() {
		LinkedList<PlaceSnapshot> placed = new LinkedList<>();

		for(PlaceSnapshot snapshot : placeSnapshots) {
			if(wand.isEmpty() || wandItem.getLimit(player, wand) == 0) continue;

			BlockPos pos = snapshot.pos;
			BlockItem placeItem = snapshot.item;

			if(placeBlock(snapshot)) {
				wand.damage(1, player, (e) -> e.sendToolBreakStatus(player.getActiveHand()));

				// If the item cant be taken, undo the placement
				if(takeItems(placeItem, 1) == 0) placed.add(snapshot);
				else {
					ConstructionWand.LOGGER.info("Item could not be taken. Remove block: "+placeItem.toString());
					world.removeBlock(pos, false);
				}
			}
		}
		placeSnapshots = placed;

		// Play place sound
		if(!placeSnapshots.isEmpty()) {
			BlockSoundGroup sound = placeSnapshots.getFirst().block.getSoundGroup();
			world.playSound(null, WandUtil.playerPos(player), sound.getPlaceSound(), SoundCategory.BLOCKS, sound.volume, sound.pitch);
		}

		// Add to job history for undo
		if(placeSnapshots.size() > 1) ConstructionWand.instance.undoHistory.add(player, world, placeSnapshots);

		return !placeSnapshots.isEmpty();
	}
}
