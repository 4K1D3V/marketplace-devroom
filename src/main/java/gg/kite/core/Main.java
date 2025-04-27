package gg.kite.core;

import gg.kite.core.commands.*;
import gg.kite.core.database.MongoManager;
import gg.kite.core.gui.MarketplaceGUI;
import gg.kite.core.listeners.InventoryListener;
import gg.kite.core.utils.ConfigManager;
import gg.kite.core.utils.DiscordWebhook;
import gg.kite.core.utils.ItemUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

public class Main extends JavaPlugin {
    private MongoManager mongoManager;
    private ConfigManager configManager;
    private MarketplaceGUI marketplaceGUI;
    private Economy economy;

    @Override
    public void onEnable() {

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = getServer().getServicesManager().getRegistration(Economy.class).getProvider();
        if (economy == null) {
            getLogger().severe("No economy provider found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DiscordWebhook.initialize(this);
        ItemUtils.initialize(this);

        configManager = new ConfigManager(this);
        configManager.loadConfig();

        try {
            mongoManager = new MongoManager(configManager.getMongoConnectionString());
            mongoManager.connect();
            getLogger().info("Successfully connected to MongoDB.");
        } catch (Exception e) {
            getLogger().severe("Failed to connect to MongoDB: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        marketplaceGUI = new MarketplaceGUI(this, mongoManager, economy);

        SellCommand sellCommand = new SellCommand(this, mongoManager, configManager);
        Objects.requireNonNull(getCommand("sell")).setExecutor(sellCommand);
        Objects.requireNonNull(getCommand("sell")).setTabCompleter(sellCommand);

        MarketplaceCommand marketplaceCommand = new MarketplaceCommand(marketplaceGUI);
        Objects.requireNonNull(getCommand("marketplace")).setExecutor(marketplaceCommand);
        Objects.requireNonNull(getCommand("marketplace")).setTabCompleter(marketplaceCommand);

        BlackMarketCommand blackMarketCommand = new BlackMarketCommand(marketplaceGUI);
        Objects.requireNonNull(getCommand("blackmarket")).setExecutor(blackMarketCommand);
        Objects.requireNonNull(getCommand("blackmarket")).setTabCompleter(blackMarketCommand);

        TransactionsCommand transactionsCommand = new TransactionsCommand(mongoManager);
        Objects.requireNonNull(getCommand("transactions")).setExecutor(transactionsCommand);
        Objects.requireNonNull(getCommand("transactions")).setTabCompleter(transactionsCommand);

        AdminCommand adminCommand = new AdminCommand(mongoManager, marketplaceGUI);
        Objects.requireNonNull(getCommand("marketplace")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("marketplace")).setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new InventoryListener(marketplaceGUI), this);

        new BukkitRunnable() {
            @Override
            public void run() {
                mongoManager.cleanupExpiredListings();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20 * 60 * 60); // Run hourly
    }

    @Override
    public void onDisable() {
        if (marketplaceGUI != null) {
            marketplaceGUI.getOpenInventories().keySet().forEach(uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.closeInventory();
                }
            });
        }

        if (mongoManager != null) {
            try {
                mongoManager.disconnect();
                getLogger().info("MongoDB connection closed successfully.");
            } catch (Exception e) {
                getLogger().severe("Failed to close MongoDB connection: " + e.getMessage());
            }
            mongoManager = null;
        }

        configManager = null;
        marketplaceGUI = null;
        economy = null;

        getLogger().info("MarketPlace plugin disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MongoManager getMongoManager() {
        return mongoManager;
    }

    public Economy getEconomy() {
        return economy;
    }
}