package gg.kite.core.commands;

import gg.kite.core.database.MongoManager;
import gg.kite.core.database.PlayerData;
import gg.kite.core.gui.MarketplaceGUI;
import gg.kite.core.utils.ItemUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {
    private final MongoManager mongoManager;
    private final MarketplaceGUI marketplaceGUI;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final List<String> SUBCOMMANDS = Arrays.asList("remove", "transactions");

    public AdminCommand(MongoManager mongoManager, MarketplaceGUI marketplaceGUI) {
        this.mongoManager = mongoManager;
        this.marketplaceGUI = marketplaceGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("marketplace.admin")) {
            sender.sendMessage("You don't have permission to use admin commands!");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("admin")) {
            sender.sendMessage("Usage: /marketplace admin <remove|transactions> <args>");
            return true;
        }

        String subcommand = args[1].toLowerCase();

        if (subcommand.equals("remove")) {
            if (args.length != 3) {
                sender.sendMessage("Usage: /marketplace admin remove <listingId>");
                return true;
            }

            String listingId = args[2];
            try {
                new ObjectId(listingId); // Validate ObjectId
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Invalid listing ID!");
                return true;
            }

            mongoManager.removeListing(listingId)
                    .thenRun(() -> {
                        sender.sendMessage("Listing " + listingId + " removed successfully!");
                        marketplaceGUI.refreshInventories();
                    })
                    .exceptionally(ex -> {
                        sender.sendMessage("Error removing listing: " + ex.getMessage());
                        return null;
                    });

            return true;
        }

        if (subcommand.equals("transactions")) {
            if (args.length != 3) {
                sender.sendMessage("Usage: /marketplace admin transactions <player>");
                return true;
            }

            UUID playerId;
            try {
                playerId = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
            } catch (Exception e) {
                sender.sendMessage("Player not found!");
                return true;
            }

            mongoManager.getPlayerData(playerId).thenAccept(playerData -> {
                List<PlayerData.Transaction> transactions = playerData.getTransactions();
                if (transactions.isEmpty()) {
                    sender.sendMessage("No transaction history for " + args[2]);
                    return;
                }

                sender.sendMessage("§6=== Transaction History for " + args[2] + " ===");
                for (PlayerData.Transaction transaction : transactions) {
                    String role = transaction.getBuyer().equals(playerId) ? "Bought" : "Sold";
                    String otherParty = transaction.getBuyer().equals(playerId) ? transaction.getSeller().toString() : transaction.getBuyer().toString();
                    String otherName = Bukkit.getOfflinePlayer(UUID.fromString(otherParty)).getName();
                    String formattedDate = dateFormat.format(new Date(transaction.getTimestamp()));

                    sender.sendMessage(String.format(
                            "§e%s: %s %s for $%.2f from/to %s%s",
                            formattedDate,
                            role,
                            transaction.getItem().getType().name(),
                            transaction.getPrice(),
                            otherName != null ? otherName : "Unknown",
                            transaction.isBlackMarket() ? " (Black Market)" : ""
                    ));
                }
                sender.sendMessage("§6=====================");
            }).exceptionally(ex -> {
                sender.sendMessage("Error fetching transaction history: " + ex.getMessage());
                return null;
            });

            return true;
        }

        sender.sendMessage("Unknown subcommand! Use: remove, transactions");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("marketplace.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("admin");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            String input = args[1].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("transactions")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}