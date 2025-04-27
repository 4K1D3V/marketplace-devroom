package gg.kite.core.commands;

import gg.kite.core.gui.MarketplaceGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MarketplaceCommand implements CommandExecutor, TabCompleter {
    private final MarketplaceGUI marketplaceGUI;
    private static final List<String> SUBCOMMANDS = Arrays.asList("list", "admin");

    public MarketplaceCommand(MarketplaceGUI marketplaceGUI) {
        this.marketplaceGUI = marketplaceGUI;
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

        if (args.length == 0) {
            marketplaceGUI.openMarketplace(player, false, 1);
            return true;
        }

        return false; // Let AdminCommand handle subcommands
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("marketplace.view")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>(SUBCOMMANDS);
            if (sender.hasPermission("marketplace.admin")) {
                suggestions.add("admin");
            }
            return suggestions.stream()
                    .filter(sub -> sub.startsWith(input))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}