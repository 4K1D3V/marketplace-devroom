package gg.kite.core.gui;

import gg.kite.core.Main;
import gg.kite.core.database.MongoManager;
import gg.kite.core.utils.DiscordWebhook;
import gg.kite.core.utils.ItemUtils;
import net.milkbowl.vault.economy.Economy;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MarketplaceGUI {
    private final Main plugin;
    private final MongoManager mongoManager;
    private final Economy economy;
    private final Map<UUID, InventoryInfo> openInventories;
    private static final int ITEMS_PER_PAGE = 45;

    private static class InventoryInfo {
        private final Inventory inventory;
        private final String title;
        private final boolean isBlackMarket;
        private final int page;

        public InventoryInfo(Inventory inventory, String title, boolean isBlackMarket, int page) {
            this.inventory = inventory;
            this.title = title;
            this.isBlackMarket = isBlackMarket;
            this.page = page;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public String getTitle() {
            return title;
        }

        public boolean isBlackMarket() {
            return isBlackMarket;
        }

        public int getPage() {
            return page;
        }
    }

    public MarketplaceGUI(Main plugin, MongoManager mongoManager, Economy economy) {
        this.plugin = plugin;
        this.mongoManager = mongoManager;
        this.economy = economy;
        this.openInventories = new HashMap<>();
    }

    public void openMarketplace(Player player, boolean isBlackMarket, int page) {
        CompletableFuture<List<Document>> listingsFuture = isBlackMarket
                ? mongoManager.getBlackMarketListings()
                : mongoManager.getActiveListings(page, ITEMS_PER_PAGE);

        CompletableFuture<Integer> countFuture = isBlackMarket
                ? CompletableFuture.supplyAsync(() -> 10)
                : mongoManager.getActiveListingsCount();

        CompletableFuture.allOf(listingsFuture, countFuture).thenAccept(v -> {
            List<Document> listings = listingsFuture.join();
            int totalItems = countFuture.join();
            int totalPages = isBlackMarket ? 1 : (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

            String title = (isBlackMarket ? "Black Market" : "Marketplace") + " (Page " + page + ")";
            Inventory inv = openInventories.containsKey(player.getUniqueId())
                    ? openInventories.get(player.getUniqueId()).getInventory()
                    : Bukkit.createInventory(null, 54, title);
            InventoryInfo info = new InventoryInfo(inv, title, isBlackMarket, page);
            openInventories.put(player.getUniqueId(), info);

            inv.clear();

            for (int i = 0; i < listings.size() && i < ITEMS_PER_PAGE; i++) {
                Document listing = listings.get(i);
                ItemStack item = ItemUtils.deserializeItem(listing.getString("item"));
                if (item == null) {
                    continue;
                }
                double price = listing.getDouble("price");
                if (isBlackMarket) price *= 0.5; // 50% discount

                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("§7Price: $" + String.format("%.2f", price));
                lore.add("§7Seller: " + Bukkit.getOfflinePlayer(UUID.fromString(listing.getString("seller"))).getName());
                lore.add("§7Listing ID: " + listing.get("_id").toString());
                meta.setLore(lore);
                item.setItemMeta(meta);

                inv.setItem(i, item);
            }

            if (!isBlackMarket) {
                if (page > 1) {
                    ItemStack prev = new ItemStack(Material.ARROW);
                    ItemMeta prevMeta = prev.getItemMeta();
                    prevMeta.setDisplayName("§ePrevious Page");
                    prev.setItemMeta(prevMeta);
                    inv.setItem(45, prev);
                }
                if (page < totalPages) {
                    ItemStack next = new ItemStack(Material.ARROW);
                    ItemMeta nextMeta = next.getItemMeta();
                    nextMeta.setDisplayName("§eNext Page");
                    next.setItemMeta(nextMeta);
                    inv.setItem(53, next);
                }
            }

            Inventory finalInv = inv;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory() != finalInv) {
                    player.openInventory(finalInv);
                }
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§cError loading marketplace: " + ex.getMessage());
            });
            return null;
        });
    }

    public void handlePurchase(Player buyer, String listingId) {
        mongoManager.getActiveListings(1, Integer.MAX_VALUE).thenAccept(listings -> {
            Document listing = listings.stream()
                    .filter(doc -> doc.get("_id").toString().equals(listingId))
                    .findFirst()
                    .orElse(null);

            if (listing == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buyer.sendMessage("§cThis listing no longer exists!");
                });
                return;
            }

            ItemStack item = ItemUtils.deserializeItem(listing.getString("item"));
            if (item == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buyer.sendMessage("§cThis listing contains an invalid item and cannot be purchased!");
                    mongoManager.removeListing(listingId); // Clean up invalid listing
                    refreshInventories();
                });
                return;
            }

            UUID seller = UUID.fromString(listing.getString("seller"));
            double price = listing.getDouble("price");
            InventoryInfo info = openInventories.get(buyer.getUniqueId());
            boolean isBlackMarket = info != null && info.isBlackMarket();
            if (isBlackMarket) price *= 0.5;

            double finalPrice = price;
            Bukkit.getScheduler().runTask(plugin, () -> {

                if (buyer.getUniqueId().equals(seller)) {
                    buyer.sendMessage("§cYou cannot purchase your own listing!");
                    return;
                }

                new ConfirmationGUI(plugin, buyer, item, finalPrice, () -> {
                    plugin.getLogger().info("Executing purchase logic on thread: " + Thread.currentThread().getName());
                    if (!buyer.isOnline()) {
                        buyer.sendMessage("§cYou are no longer online!");
                        return;
                    }


                    double buyerBalance = economy.getBalance(buyer);
                    plugin.getLogger().info("Player " + buyer.getName() + " balance: $" + buyerBalance + ", Item price: $" + finalPrice);

                    if (!economy.has(buyer, finalPrice)) {
                        plugin.getLogger().info("Player " + buyer.getName() + " does not have enough money!");
                        buyer.sendMessage("§cYou don't have enough money! Need $" + String.format("%.2f", finalPrice) + ", but you have $" + String.format("%.2f", buyerBalance));
                        return;
                    }

                    try {
                        economy.withdrawPlayer(buyer, finalPrice);
                        economy.depositPlayer(Bukkit.getOfflinePlayer(seller), finalPrice); // Seller receives what buyer pays
                    } catch (Exception e) {
                        economy.depositPlayer(buyer, finalPrice); // Rollback
                        buyer.sendMessage("§cError processing payment!");
                        plugin.getLogger().severe("Payment error: " + e.getMessage());
                        return;
                    }

                    if (!buyer.getInventory().addItem(item).isEmpty()) {
                        economy.depositPlayer(buyer, finalPrice);
                        try {
                            economy.withdrawPlayer(Bukkit.getOfflinePlayer(seller), finalPrice);
                        } catch (Exception e) {
                            plugin.getLogger().severe("Failed to rollback seller payment: " + e.getMessage());
                        }
                        buyer.sendMessage("§cNot enough inventory space! Transaction cancelled.");
                        return;
                    }

                    mongoManager.removeListing(listingId);
                    mongoManager.addTransaction(buyer.getUniqueId(), seller, item, finalPrice, isBlackMarket);

                    CompletableFuture.runAsync(() -> {
                        DiscordWebhook.sendWebhook(plugin.getConfigManager().getDiscordWebhookUrl(),
                                String.format("Purchase: %s bought %s for $%.2f from %s%s",
                                        buyer.getName(),
                                        item.getType().name(),
                                        finalPrice,
                                        Bukkit.getOfflinePlayer(seller).getName(),
                                        isBlackMarket ? " (Black Market)" : ""));
                    });

                    double buyerBalanceAfter = economy.getBalance(buyer);
                    double sellerBalanceAfter = economy.getBalance(Bukkit.getOfflinePlayer(seller));
                    plugin.getLogger().info("Transaction completed: " + buyer.getName() + " paid $" + finalPrice + " (balance after: $" + buyerBalanceAfter + "), seller " + Bukkit.getOfflinePlayer(seller).getName() + " received $" + finalPrice + " (balance after: $" + sellerBalanceAfter + ")");

                    buyer.sendMessage("§aPurchase successful!");
                    refreshInventories();
                });
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Handling purchase exception on thread: " + Thread.currentThread().getName());
                buyer.sendMessage("§cError processing purchase: " + ex.getMessage());
            });
            return null;
        });
    }

    public void handlePagination(Player player, boolean nextPage) {
        InventoryInfo info = openInventories.get(player.getUniqueId());
        if (info == null || info.isBlackMarket()) {
            return;
        }

        int currentPage = info.getPage();
        int newPage = nextPage ? currentPage + 1 : currentPage - 1;
        if (newPage < 1) return;

        openMarketplace(player, false, newPage);
    }

    private int extractPageFromTitle(String title) {
        try {
            String pageStr = title.split("Page ")[1].replace(")", "");
            return Integer.parseInt(pageStr);
        } catch (Exception e) {
            return 1;
        }
    }

    public void refreshInventories() {
        for (Map.Entry<UUID, InventoryInfo> entry : new HashMap<>(openInventories).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            InventoryInfo info = entry.getValue();
            if (player != null && player.getOpenInventory().getTopInventory().equals(info.getInventory())) {
                openMarketplace(player, info.isBlackMarket(), info.getPage());
            } else {
                openInventories.remove(entry.getKey());
            }
        }
    }

    public Map<UUID, Inventory> getOpenInventories() {
        Map<UUID, Inventory> inventories = new HashMap<>();
        openInventories.forEach((uuid, info) -> inventories.put(uuid, info.getInventory()));
        return inventories;
    }
}