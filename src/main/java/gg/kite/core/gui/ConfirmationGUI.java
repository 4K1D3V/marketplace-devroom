package gg.kite.core.gui;

import gg.kite.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ConfirmationGUI implements Listener {
    private final Main plugin;
    private final Player player;
    private final Inventory inventory;
    private final Runnable onConfirm;
    private boolean isProcessed;

    public ConfirmationGUI(@NotNull Main plugin, @NotNull Player player, @NotNull ItemStack item, double price, Runnable onConfirm) {
        this.plugin = plugin;
        this.player = player;
        this.onConfirm = onConfirm;
        this.isProcessed = false;

        this.inventory = Bukkit.createInventory(null, 27, "Confirm Purchase");

        ItemStack confirmItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName("§aConfirm Purchase");
        confirmMeta.setLore(Arrays.asList("§7Click to purchase for $" + String.format("%.2f", price)));
        confirmItem.setItemMeta(confirmMeta);

        ItemStack displayItem = item.clone();
        ItemMeta displayMeta = displayItem.getItemMeta();
        displayMeta.setDisplayName("§eItem to Purchase");
        displayItem.setItemMeta(displayMeta);

        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName("§cCancel");
        cancelMeta.setLore(Arrays.asList("§7Click to cancel the purchase"));
        cancelItem.setItemMeta(cancelMeta);

        inventory.setItem(11, confirmItem);
        inventory.setItem(13, displayItem);
        inventory.setItem(15, cancelItem);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getLogger().info("Opening ConfirmationGUI for " + player.getName() + " on thread: " + Thread.currentThread().getName());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory || event.getWhoClicked() != player) {
            return;
        }

        plugin.getLogger().info("Handling inventory click on thread: " + Thread.currentThread().getName());
        event.setCancelled(true);

        if (isProcessed) {
            return;
        }

        // Buttons: Confirm and Close..
        int slot = event.getRawSlot();
        if (slot == 11) {
            isProcessed = true;
            onConfirm.run();
            player.closeInventory();
        } else if (slot == 15) {
            isProcessed = true;
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != inventory || event.getPlayer() != player) {
            return;
        }

        plugin.getLogger().info("Handling inventory close on thread: " + Thread.currentThread().getName());
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
}