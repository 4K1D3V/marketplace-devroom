package gg.kite.core.utils;

import gg.kite.core.Main;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final Main plugin;
    private FileConfiguration config;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
    }

    public String getMongoConnectionString() {
        return config.getString("mongodb.connection-string", "mongodb://localhost:27017");
    }

    public String getDiscordWebhookUrl() {
        return config.getString("discord.webhook-url", "");
    }

    public int getMaxListingsPerPlayer() {
        return config.getInt("marketplace.max-listings-per-player", 10);
    }
}