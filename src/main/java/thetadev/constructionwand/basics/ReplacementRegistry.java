package thetadev.constructionwand.basics;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import thetadev.constructionwand.ConstructionWand;

import java.util.HashSet;
import java.util.Set;

public class ReplacementRegistry
{
	private static final HashSet<HashSet<Item>> replacements = new HashSet<>();

	public static void init() {
		for(Object key : ConfigServer.SIMILAR_BLOCKS.get()) {
			if(!(key instanceof String)) continue;
			HashSet<Item> set = new HashSet<>();

			for(String id : ((String)key).split(";")) {
				Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
				if(item == null) {
					ConstructionWand.LOGGER.warn("Replacement Registry: Could not find item "+id);
					continue;
				}
				set.add(item);
			}
			if(!set.isEmpty()) replacements.add(set);
		}
	}

	public static Set<Item> getMatchingSet(Item item) {
		HashSet<Item> res = new HashSet<>();

		for(HashSet<Item> set : replacements) {
			if(set.contains(item)) res.addAll(set);
		}
		res.remove(item);
		return res;
	}

	public static boolean matchBlocks(Block b1, Block b2) {
		if(b1 == b2) return true;

		for(HashSet<Item> set : replacements) {
			if(set.contains(b1.asItem()) && set.contains(b2.asItem())) return true;
		}
		return false;
	}
}
