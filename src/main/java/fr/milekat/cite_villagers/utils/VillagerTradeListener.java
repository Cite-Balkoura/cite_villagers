package fr.milekat.cite_villagers.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import fr.milekat.cite_core.utils_tools.Tools;

public class VillagerTradeListener implements Listener {

    private static final Set<InventoryAction> purchaseSingleItemActions;
    static {
        // Each of these action types are single order purchases that do not require free inventory space to satisfy.
        // I.e. they stack up on the cursor (hover under the mouse).
        purchaseSingleItemActions = new HashSet<>();
        purchaseSingleItemActions.add(InventoryAction.PICKUP_ONE);
        purchaseSingleItemActions.add(InventoryAction.PICKUP_ALL);
        purchaseSingleItemActions.add(InventoryAction.PICKUP_HALF);
        purchaseSingleItemActions.add(InventoryAction.PICKUP_SOME);
        purchaseSingleItemActions.add(InventoryAction.DROP_ONE_SLOT);
        purchaseSingleItemActions.add(InventoryAction.DROP_ALL_SLOT);  // strangely in trades this is a single item event
        purchaseSingleItemActions.add(InventoryAction.HOTBAR_SWAP);
    }

    /** Because there is no Inventory:clone method */
    public static class InventorySnapshot implements InventoryHolder {
        Inventory inventory;

        public InventorySnapshot(Inventory inv) {
            ItemStack[] source = inv.getStorageContents();
            inventory = Bukkit.createInventory(this, source.length, "Snapshot");
            for (int i = 0; i < source.length; i++) {
                inventory.setItem(i, source[i] != null ? source[i].clone() : null);
            }
        }
        public InventorySnapshot(int size) {
            inventory = Bukkit.createInventory(this, size, "Snapshot");
        }
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static class VillagerTradeEvent extends Event implements Cancellable {
        private static final HandlerList handlers = new HandlerList();
        final HumanEntity player;
        final MerchantRecipe recipe;
        final int offerIndex;
        final int orders;
        final int ingredientOneDiscountedPrice;
        final int ingredientOneTotalAmount;
        final int ingredientTwoTotalAmount;
        final int amountPurchased;
        final int amountLost;
        boolean cancelled = false;

        public VillagerTradeEvent(HumanEntity player, MerchantRecipe recipe, int offerIndex,
                                  int orders, int ingredientOneDiscountedPrice,
                                  int amountPurchased, int amountLost) {
            this.player = player;
            this.recipe = recipe;
            this.offerIndex = offerIndex;
            this.orders = orders;
            this.ingredientOneDiscountedPrice = ingredientOneDiscountedPrice;
            this.amountPurchased = amountPurchased;
            this.amountLost = amountLost;

            ingredientOneTotalAmount = ingredientOneDiscountedPrice * orders;
            if (recipe.getIngredients().size() > 1) {
                ItemStack bb = recipe.getIngredients().get(1);
                ingredientTwoTotalAmount = bb.getType() != Material.AIR ? bb.getAmount() * orders : 0;
            } else {
                ingredientTwoTotalAmount = 0;
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }

        /** Cancels the trade. Note the client will need to close and reopen the trade window to, for example, see that
         * a canceled trade is not sold out.
         */
        public void setCancelled(boolean cancel) {
            cancelled = cancel;
        }

        public HumanEntity getPlayer() {
            return player;
        }

        public MerchantRecipe getRecipe() {
            return recipe;
        }

        /** For the total count of the item purchased use {@code getAmountPurchased}.*/
        public int getOrders() {
            return orders;
        }

        public int getOfferIndex() {
            return offerIndex;
        }

        /**
         * The actual amount of ingredient one charged for a single 'order'; e.g. the price after all
         * gossip/player-reputation and hero of the village effects have been applied.
         * Note that only the first ingredient is discounted by the villager.
         * @return amount of item 1 each order actually cost.
         */
        public int getIngredientOneDiscountedPrice() {
            return ingredientOneDiscountedPrice;
        }

        /** The total amount of {@code recipe.getIngredients().get(0)} spent
         * @return*/
        public int getIngredientOneTotalAmount() {
            return ingredientOneTotalAmount;
        }
        /** The total amount of {@code recipe.getIngredients().get(1)} spent, or zero if no ingredient 2*/
        public int getIngredientTwoTotalAmount() {
            return ingredientTwoTotalAmount;
        }

        public String getBestNameForIngredientOne() {
            return bestNameFor(recipe.getIngredients().get(0));
        }

        public String getBestNameForIngredientTwo() {
            if (recipe.getIngredients().size() > 1) {
                ItemStack stack = recipe.getIngredients().get(1);
                if (stack != null)
                    return bestNameFor(stack);
            }
            return null;
        }

        public String getBestNameForResultItem() {
            return bestNameFor(recipe.getResult());
        }

        /** Total amount of {@code recipe.getResult()} purchased. This value is the total count the player received. */
        public int getAmountPurchased() {
            return amountPurchased;
        }

        /** When the player does not have inventory space for all of the items purchased they may drop or simply
         * be lost. I've seen both happen.*/
        public int getAmountLost() {
            return amountLost;
        }

        public HandlerList getHandlers() {
            return handlers;
        }

        public static HandlerList getHandlerList() {
            return handlers;
        }

        static private String bestNameFor(ItemStack stack) {
            if (stack == null) return "null";
            if (stack.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta)stack.getItemMeta();
                if (meta != null && meta.hasTitle() && meta.getTitle() != null)
                    return ChatColor.stripColor(meta.getTitle());
            }
            if (stack.getItemMeta() != null) {
                ItemMeta meta = stack.getItemMeta();
                if (meta.hasDisplayName())
                    return ChatColor.stripColor(meta.getDisplayName());
            }
            return stack.getType().name();
        }
    }

    public VillagerTradeListener(JavaPlugin owner) {
        owner.getServer().getPluginManager().registerEvents(this,owner);
    }

    /**
     * Calculates if the given stacks are of the exact same item thus could be stacked.
     * @return true - stacks can be combined (assuming a max stack size > 1).
     */
    public static boolean areStackable(ItemStack a, ItemStack b) {
        if (a == null && b == null || (a == null && b.getType() == Material.AIR)
                || (b == null && a.getType() == Material.AIR)) return true;
        if (a == null || b == null || a.getType() != b.getType()) return false;
        if (a.getItemMeta() == null && b.getItemMeta() == null) return true;
        if (a.getItemMeta() == null || b.getItemMeta() == null) return false;
        return a.getItemMeta().equals(b.getItemMeta());
    }

    @EventHandler
    public void onInventoryClickEvent(final InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.NOTHING) return;
        if (event.getInventory().getType() != InventoryType.MERCHANT) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        final HumanEntity player = event.getWhoClicked();
        final MerchantInventory merchantInventory = (MerchantInventory)event.getInventory();
        final MerchantRecipe recipe = merchantInventory.getSelectedRecipe();

        if (recipe == null) return;
        ItemStack A = recipe.getIngredients().get(0);
        float multiplier = recipe.getPriceMultiplier();
        if (multiplier==0.0f){
            multiplier = 1.0f;
        }
        int discountedPriceA = Math.round(A.getAmount() * multiplier);
        if (discountedPriceA==0){
            discountedPriceA = 1;
        }
        final int maxUses = recipe.getMaxUses() - recipe.getUses();

        VillagerTradeEvent vtEvent = null;
        if (purchaseSingleItemActions.contains(event.getAction())) {
            vtEvent = new VillagerTradeEvent(
                    player, recipe, merchantInventory.getSelectedRecipeIndex(),
                    1, discountedPriceA,
                    recipe.getResult().getAmount(), 0
            );
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // This situation is where the player SHIFT+CLICKS the output item to buy multiple times at once.
            // Because this event is fired before any inventories have changed - we need to simulate what will happen
            // when the inventories update.
            InventorySnapshot playerSnap = new InventorySnapshot(player.getInventory());
            InventorySnapshot merchantSnap = new InventorySnapshot(9);
            for (int i = 0; i < 3; i++) {
                if (merchantInventory.getItem(i) != null)
                    merchantSnap.getInventory().setItem(i, merchantInventory.getItem(i).clone());
            }
            List<ItemStack> ingredients = recipe.getIngredients();
            ItemStack ma = merchantSnap.getInventory().getItem(0);
            ItemStack mb = merchantSnap.getInventory().getItem(1);
            ItemStack ra = ingredients.get(0);
            ItemStack rb = ingredients.size() > 1 ? ingredients.get(1) : null;
            if (rb != null && rb.getType() == Material.AIR) rb = null;

            if (areStackable(ra, mb)) {
                ItemStack tmp = ma;
                ma = mb;
                mb = tmp;
            }

            int amount = ma.getAmount() / discountedPriceA;
            if (rb != null && mb != null && rb.getType() != Material.AIR && mb.getType() != Material.AIR) {
                amount = Math.min(amount, mb.getAmount() / rb.getAmount());
            }
            amount = clamp(amount, 0, maxUses);

            // In order for "failed" below to be populated we need to compute each stack here
            int maxStackSize = recipe.getResult().getMaxStackSize();
            List<ItemStack> stacks = new ArrayList<>();
            int unaccounted = amount;
            while (unaccounted != 0) {
                ItemStack stack = recipe.getResult().clone();
                stack.setAmount(Math.min(maxStackSize, unaccounted));
                stacks.add(stack);
                unaccounted -= stack.getAmount();
            }
            HashMap<Integer, ItemStack> failed = playerSnap.getInventory().addItem(stacks.toArray(new ItemStack[0]));
            int loss = 0;
            if (!failed.isEmpty()) {
                for (ItemStack stack : failed.values()) {
                    amount -= stack.getAmount();
                }
                // If a partial result is delivered, the rest of it is dropped... or just lost... I've seen both happen
                int rem = amount % recipe.getResult().getAmount();
                if (rem != 0) {
                    loss = recipe.getResult().getAmount() - rem;
                    amount += loss;
                }
            }
            int orders = 0;
            for (int i=1;i<=amount;i++){
                if (Tools.canStore(event.getWhoClicked().getInventory(),36,recipe.getResult(),i)) {
                    orders++;
                }
            }
            amount = orders * recipe.getResult().getAmount();
            vtEvent = new VillagerTradeEvent(
                    player, recipe, merchantInventory.getSelectedRecipeIndex(),
                    orders, discountedPriceA,
                    amount, loss
            );
        }
        if (vtEvent != null) {
            vtEvent.setCancelled(event.isCancelled());
            Bukkit.getPluginManager().callEvent(vtEvent);
            event.setCancelled(vtEvent.isCancelled());
        }
    }

    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
