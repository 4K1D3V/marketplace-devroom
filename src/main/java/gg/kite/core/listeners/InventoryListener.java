package gg.kite.core.listeners;

import gg.kite.core.gui.MarketplaceGUI;
import org.bson.types.ObjectId;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.util.List;

public class InventoryListener implements Listener {
    private final MarketplaceGUI marketplaceGUI;

    public InventoryListener(MarketplaceGUI marketplaceGUI) {
        this.marketplaceGUI = marketplaceGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();

        // Check if the clicked inventory is a marketplace or black market GUI
        if (!marketplaceGUI.getOpenInventories().containsValue(inventory)) {
            return;
        }

        // Cancel the event to prevent item manipulation
        event.setCancelled(true);

        // Ignore clicks outside the top inventory or invalid slots
        if (event.getRawSlot() >= inventory.getSize() || event.getRawSlot() < 0) {
            return;
        }

        // Handle pagination
        if (event.getRawSlot() == 45 && inventory.getItem(45) != null) {
            marketplaceGUI.handlePagination(player, false); // Previous page
            return;
        }
        if (event.getRawSlot() == 53 && inventory.getItem(53) != null) {
            marketplaceGUI.handlePagination(player, true); // Next page
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        // Extract listing ID from item lore
        ItemMeta meta = clickedItem.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null || lore.size() < 3) {
            return;
        }

        String listingIdLine = lore.get(lore.size() - 1); // Last lore line: "Listing ID: <id>"
        if (!listingIdLine.startsWith("§7Listing ID: ")) {
            return;
        }

        String listingId = listingIdLine.replace("§7Listing ID: ", "");
        try {
            new ObjectId(listingId); // Validate ObjectId
        } catch (IllegalArgumentException e) {
            return;
        }

        marketplaceGUI.handlePurchase(player, listingId);
    }
}