package dev.fortunecrops;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Random;

public class CropFortuneListener implements Listener {

    private final FortuneCrops plugin;
    private final Random random = new Random();

    // All fully-grown crop materials we apply Fortune to.
    // MELON and PUMPKIN are intentionally excluded: they are solid blocks with no
    // Ageable data, so the plugin cannot distinguish a naturally-grown block from a
    // player-placed one — allowing Fortune on them would create an infinite item
    // duplication exploit. Vanilla already applies Fortune to Melon slice drops natively.
    private static final java.util.Set<Material> CROP_BLOCKS = java.util.EnumSet.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA
    );


    // For crops where the crop itself is also the seed (carrot, potato)
    // we DO apply fortune — only generic seeds and rare junk drops are excluded.
    private static final java.util.Set<Material> EXCLUDED_FROM_FORTUNE = java.util.EnumSet.of(
            Material.WHEAT_SEEDS,
            Material.BEETROOT_SEEDS,
            Material.POISONOUS_POTATO  // 2% chance drop; vanilla does not multiply it with Fortune
    );

    public CropFortuneListener(FortuneCrops plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Only handle crops
        if (!CROP_BLOCKS.contains(block.getType())) return;

        // Only fully grown crops (all supported crops are Ageable)
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        } else {
            return;
        }

        // Check Fortune level on the held item
        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);

        // No fortune? Let vanilla handle it normally.
        if (fortuneLevel <= 0) return;

        // IMPORTANT: strip Fortune from the dummy tool before calling getDrops().
        // block.getDrops(tool, player) runs the full loot table including Fortune
        // bonuses, so passing the real tool would bake in a multiplier that we then
        // multiply again — resulting in double-Fortune. Using a Fortune-free clone
        // gives us the true base drops that we can then multiply ourselves exactly once.
        ItemStack dummyTool = tool.clone();
        dummyTool.removeEnchantment(Enchantment.FORTUNE);
        Collection<ItemStack> baseDrops = block.getDrops(dummyTool, player);

        // Cancel the event and re-drop with fortune applied
        event.setDropItems(false);

        for (ItemStack drop : baseDrops) {
            if (drop == null || drop.getType() == Material.AIR) continue;

            if (EXCLUDED_FROM_FORTUNE.contains(drop.getType())) {
                // Drop seeds unchanged
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            } else {
                // Apply fortune multiplier (same algorithm as ore fortune)
                int originalAmount = drop.getAmount();
                int fortunateAmount = applyFortune(originalAmount, fortuneLevel);
                drop.setAmount(fortunateAmount);
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }
    }

    /**
     * Replicates the exact ore-fortune algorithm from the wiki table.
     *
     * Fortune I:  66% chance of 1×, 33% chance of 2×  → avg 1.33×
     * Fortune II: 50% chance of 1×, 25% chance of 2×, 25% chance of 3×  → avg 1.75×
     * Fortune III: 40% 1×, 20% 2×, 20% 3×, 20% 4×  → avg 2.2×
     *
     * The general formula Minecraft uses internally:
     *   Pick a random integer in [0, fortuneLevel + 2)
     *   The multiplier is max(1, roll)  — rolling 0 or 1 both count as 1×
     *
     * This matches the wiki distribution exactly.
     */
    private int applyFortune(int baseAmount, int fortuneLevel) {
        // Random int from 0 (inclusive) to (fortuneLevel + 2) (exclusive)
        int roll = random.nextInt(fortuneLevel + 2);
        int multiplier = Math.max(1, roll);
        return baseAmount * multiplier;
    }
}
