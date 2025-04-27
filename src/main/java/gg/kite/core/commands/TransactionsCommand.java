package gg.kite.core.commands;

import gg.kite.core.database.MongoManager;
import gg.kite.core.database.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TransactionsCommand implements CommandExecutor, TabCompleter {
    private final MongoManager mongoManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int TRANSACTIONS_PER_PAGE = 5;
    private static final List<String> SUBCOMMANDS = Arrays.asList("buy", "sell", "page");

    public TransactionsCommand(MongoManager mongoManager) {
        this.mongoManager = mongoManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("marketplace.history")) {
            player.sendMessage("You don't have permission to view transaction history!");
            return true;
        }

        UUID playerId = player.getUniqueId();
        int page = 1;
        String filter = null;

        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("sell")) {
                filter = args[0].toLowerCase();
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                        if (page < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage("Please enter a valid page number!");
                        return true;
                    }
                }
            } else {
                try {
                    page = Integer.parseInt(args[0]);
                    if (page < 1) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage("Please enter a valid page number!");
                    return true;
                }
            }
        }

        final String finalFilter = filter;
        final int finalPage = page;

        player.sendMessage("Fetching transaction history...");

        mongoManager.getPlayerData(playerId).thenAccept(playerData -> {
            List<PlayerData.Transaction> transactions = playerData.getTransactions();
            if (finalFilter != null) {
                transactions = transactions.stream()
                        .filter(t -> finalFilter.equals("buy") ? t.getBuyer().equals(playerId) : t.getSeller().equals(playerId))
                        .collect(Collectors.toList());
            }

            if (transactions.isEmpty()) {
                player.sendMessage("You have no transaction history.");
                return;
            }

            int totalPages = (int) Math.ceil((double) transactions.size() / TRANSACTIONS_PER_PAGE);
            if (finalPage > totalPages) {
                player.sendMessage("Invalid page number! Max page: " + totalPages);
                return;
            }

            int start = (finalPage - 1) * TRANSACTIONS_PER_PAGE;
            int end = Math.min(start + TRANSACTIONS_PER_PAGE, transactions.size());

            player.sendMessage("§6=== Transaction History (Page " + finalPage + "/" + totalPages + ") ===");
            for (int i = start; i < end; i++) {
                PlayerData.Transaction transaction = transactions.get(i);
                String role = transaction.getBuyer().equals(playerId) ? "Bought" : "Sold";
                String otherParty = transaction.getBuyer().equals(playerId) ? transaction.getSeller().toString() : transaction.getBuyer().toString();
                String otherName = Bukkit.getOfflinePlayer(UUID.fromString(otherParty)).getName();
                String formattedDate = dateFormat.format(new Date(transaction.getTimestamp()));

                player.sendMessage(String.format(
                        "§e%s: %s %s for $%.2f from/to %s%s",
                        formattedDate,
                        role,
                        transaction.getItem().getType().name(),
                        transaction.getPrice(),
                        otherName != null ? otherName : "Unknown",
                        transaction.isBlackMarket() ? " (Black Market)" : ""
                ));
            }
            player.sendMessage("§6Use /transactions [buy|sell] <page> to filter or navigate");
            player.sendMessage("§6=====================");
        }).exceptionally(ex -> {
            player.sendMessage("§cError fetching transaction history: " + ex.getMessage());
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("marketplace.history")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("sell"))) {
            return Arrays.asList("1", "2", "3");
        }

        return new ArrayList<>();
    }
}