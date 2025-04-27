package gg.kite.core.commands;

import gg.kite.core.Main;
import gg.kite.core.database.MongoManager;
import gg.kite.core.gui.MarketplaceGUI;
import gg.kite.core.utils.ConfigManager;
import gg.kite.core.utils.ItemUtils;
import net.milkbowl.vault.economy.Economy;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MarketplaceCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final MarketplaceGUI marketplaceGUI;
    private final MongoManager mongoManager;
    private final ConfigManager configManager;
    private final Economy economy;
    private final AdminCommand adminCommand;
    private static final List<String> SUBCOMMANDS = Arrays.asList("gui", "sell", "blackmarket",
            "transactions", "list", "admin");

    public MarketplaceCommand(Main plugin, MarketplaceGUI marketplaceGUI, MongoManager mongoManager,
                              ConfigManager configManager, Economy economy) {
        this.plugin = plugin;
        this.marketplaceGUI = marketplaceGUI;
        this.mongoManager = mongoManager;
        this.configManager = configManager;
        this.economy = economy;
        this.adminCommand = new AdminCommand(mongoManager, marketplaceGUI);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("marketplace.view")) {
            player.sendMessage("You don't have permission to view the marketplace!");
            return true;
        }

        // SUBCOMMANDS FOR MARKETPLACE..
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("gui"))) {
            marketplaceGUI.openMarketplace(player, false, 1);
            return true;
        }

        if (args.length >= 1) {
            String subcommand = args[0].toLowerCase();

            switch (subcommand) {
                case "sell":
                    if (!player.hasPermission("marketplace.sell")) {
                        player.sendMessage("You don't have permission to sell items!");
                        return true;
                    }

                    if (args.length != 2) {
                        player.sendMessage("§cUsage: /marketplace sell <price>");
                        return true;
                    }

                    double price;
                    try {
                        price = Double.parseDouble(args[1]);
                        if (price <= 0) {
                            player.sendMessage("§cPrice must be greater than 0!");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid price format!");
                        return true;
                    }

                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item.getType().isAir()) {
                        player.sendMessage("§cYou must hold an item to sell!");
                        return true;
                    }

                    double listingFee = price * 0.10;
                    if (!economy.has(player, listingFee)) {
                        player.sendMessage("§cYou don't have enough money to pay the listing fee ($" + String.format("%.2f", listingFee) + ")!");
                        return true;
                    }

                    economy.withdrawPlayer(player, listingFee);
                    ItemStack itemToSell = item.clone();
                    item.setAmount(0);
                    mongoManager.addListing(player.getUniqueId(), itemToSell, price);
                    player.sendMessage("§aItem listed for $" + String.format("%.2f", price) + " (Listing fee: $" + String.format("%.2f", listingFee) + ")");
                    return true;

                case "blackmarket":
                    marketplaceGUI.openMarketplace(player, true, 1);
                    return true;

                case "transactions":
                    CompletableFuture<List<Document>> transactionsFuture = mongoManager.getTransactions(player.getUniqueId());
                    transactionsFuture.thenAccept(transactions -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (transactions.isEmpty()) {
                                player.sendMessage("§eYou have no transaction history.");
                                return;
                            }

                            player.sendMessage("§e=== Transaction History ===");
                            for (Document transaction : transactions) {
                                UUID buyer = UUID.fromString(transaction.getString("buyer"));
                                UUID seller = UUID.fromString(transaction.getString("seller"));
                                String itemName = transaction.getString("itemName");
                                double price1 = transaction.getDouble("price");
                                boolean isBlackMarket = transaction.getBoolean("isBlackMarket", false);

                                player.sendMessage(String.format("§7Buyer: %s, Seller: %s, Item: %s, Price: $%.2f%s",
                                        Bukkit.getOfflinePlayer(buyer).getName(),
                                        Bukkit.getOfflinePlayer(seller).getName(),
                                        itemName,
                                        price1,
                                        isBlackMarket ? " (Black Market)" : ""));
                            }
                        });
                    }).exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§cError fetching transactions: " + ex.getMessage());
                        });
                        return null;
                    });
                    return true;

                case "list":
                    CompletableFuture<List<Document>> listingsFuture = mongoManager.getListingsByPlayer(player.getUniqueId());
                    listingsFuture.thenAccept(listings -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (listings.isEmpty()) {
                                player.sendMessage("§eYou have no active listings.");
                                return;
                            }

                            player.sendMessage("§e=== Your Active Listings ===");
                            for (Document listing : listings) {
                                String listingId = listing.get("_id").toString();
                                String shortId = listingId.length() > 8 ? listingId.substring(listingId.length() - 8) : listingId;
                                String itemSerialized = listing.getString("item");
                                ItemStack itemStack = ItemUtils.deserializeItem(itemSerialized);
                                String itemName = itemStack != null ? (itemStack.hasItemMeta()
                                        && itemStack.getItemMeta().hasDisplayName() ? itemStack.getItemMeta().getDisplayName()
                                        : itemStack.getType().name()) : "Unknown Item";

                                double listingPrice = listing.getDouble("price");

                                player.sendMessage(String.format("§7ID: %s, Item: %s, Price: $%.2f",
                                        shortId, itemName, listingPrice));
                            }
                        });
                    }).exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§cError fetching listings: " + ex.getMessage());
                        });
                        return null;
                    });
                    return true;

                case "admin":
                    if (!player.hasPermission("marketplace.admin")) {
                        player.sendMessage("You don't have permission to use admin commands!");
                        return true;
                    }
                    return adminCommand.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
            }
        }

        player.sendMessage("§cUsage: /marketplace [gui|sell|blackmarket|transactions|list|admin]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("marketplace.view")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>(SUBCOMMANDS);
            if (!sender.hasPermission("marketplace.admin")) {
                suggestions.remove("admin");
            }
            if (!sender.hasPermission("marketplace.sell")) {
                suggestions.remove("sell");
            }
            return suggestions.stream()
                    .filter(sub -> sub.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("marketplace.admin")) {
            return adminCommand.onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sell") && sender.hasPermission("marketplace.sell")) {
            return Arrays.asList("<price>");
        }

        return new ArrayList<>();
    }
}