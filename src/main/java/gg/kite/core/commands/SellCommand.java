package gg.kite.core.commands;

import gg.kite.core.Main;
import gg.kite.core.database.MongoManager;
import gg.kite.core.utils.ConfigManager;
import gg.kite.core.utils.ItemUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SellCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final MongoManager mongoManager;
    private final ConfigManager configManager;
    private static final List<String> PRICE_SUGGESTIONS = Arrays.asList("10", "50", "100", "500", "1000");

    public SellCommand(Main plugin, MongoManager mongoManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.mongoManager = mongoManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("marketplace.sell")) {
            player.sendMessage("You don't have permission to sell items!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("Usage: /sell <price>");
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(args[0]);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage("Please enter a valid positive price!");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage("You must hold an item to sell!");
            return true;
        }

        // Validate item serialization
        String serialized = ItemUtils.serializeItem(item);
        if (serialized.isEmpty()) {
            player.sendMessage("This item cannot be listed due to an internal error!");
            return true;
        }
        ItemStack deserialized = ItemUtils.deserializeItem(serialized);
        if (deserialized == null) {
            player.sendMessage("This item cannot be listed due to an internal error!");
            return true;
        }

        // Check listing limit
        int maxListings = configManager.getMaxListingsPerPlayer();
        if (mongoManager.getPlayerListingCount(player.getUniqueId()) >= maxListings) {
            player.sendMessage("You have reached the maximum number of listings (" + maxListings + ")!");
            return true;
        }

        ItemStack itemClone = item.clone();
        player.getInventory().setItemInMainHand(null);

        mongoManager.addListing(player.getUniqueId(), itemClone, price)
                .thenRun(() -> player.sendMessage("Item listed for $" + price))
                .exceptionally(ex -> {
                    player.getInventory().addItem(itemClone);
                    player.sendMessage("Error listing item: " + ex.getMessage());
                    return null;
                });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("marketplace.sell")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return PRICE_SUGGESTIONS.stream()
                    .filter(price -> price.startsWith(input))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}