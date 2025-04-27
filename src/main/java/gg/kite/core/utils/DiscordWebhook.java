package gg.kite.core.utils;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DiscordWebhook {
    private static final List<WebhookMessage> webhookQueue = new ArrayList<>();
    private static boolean isProcessing = false;
    private static JavaPlugin plugin;

    public static void initialize(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void sendWebhook(String webhookUrl, String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        webhookQueue.add(new WebhookMessage(webhookUrl, message));
        if (!isProcessing) {
            processQueue();
        }
    }

    private static void processQueue() {
        if (webhookQueue.isEmpty()) {
            isProcessing = false;
            return;
        }

        isProcessing = true;
        WebhookMessage webhook = webhookQueue.removeFirst();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhook.webhookUrl);
            httpPost.setHeader("Content-Type", "application/json");

            String jsonPayload = String.format("{\"content\": \"%s\"}", escapeJson(webhook.message));
            StringEntity entity = new StringEntity(jsonPayload);
            httpPost.setEntity(entity);

            httpClient.execute(httpPost);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook: " + e.getMessage());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                processQueue();
            }
        }.runTaskLater(plugin, 20L);
    }

    private static String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class WebhookMessage {
        final String webhookUrl;
        final String message;

        WebhookMessage(String webhookUrl, String message) {
            this.webhookUrl = webhookUrl;
            this.message = message;
        }
    }
}