package gg.kite.core.commands;

import gg.kite.core.gui.MarketplaceGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BlackMarketCommand implements CommandExecutor, TabCompleter {
    private final MarketplaceGUI marketplaceGUI;

    public BlackMarketCommand(MarketplaceGUI marketplaceGUI) {
        this.marketplaceGUI = marketplaceGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("marketplace.blackmarket")) {
            player.sendMessage("You don't have permission to access the black market!");
            return true;
        }

        marketplaceGUI.openMarketplace(player, true, 1);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("marketplace.blackmarket")) {
            return new ArrayList<>();
        }

        return new ArrayList<>(); // No subcommands yet
    }
}